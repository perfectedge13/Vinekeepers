package com.vinekeepers.workflow.actions;



import com.vinekeepers.core.cursor.CursorAgentLaunchRequest;

import com.vinekeepers.core.cursor.CursorAgentLaunchResult;

import com.vinekeepers.core.cursor.CursorCloudAdapter;

import com.vinekeepers.core.cursor.CursorCloudException;

import com.vinekeepers.core.cursor.CursorInstructionComposer;

import com.vinekeepers.core.cursor.LifecycleRunRecord;

import com.vinekeepers.env.Env;

import com.vinekeepers.events.Event;

import com.vinekeepers.state.LifecycleContextStore;

import com.vinekeepers.state.StateStore;

import com.vinekeepers.state.planning.FeaturePlanState;

import com.vinekeepers.state.planning.FeaturePlanStateStore;

import com.vinekeepers.state.planning.PlanApproval;

import com.vinekeepers.state.planning.PlanApprovalStatus;

import org.slf4j.Logger;

import org.slf4j.LoggerFactory;



import java.time.Instant;

import java.util.LinkedHashMap;

import java.util.Locale;

import java.util.Map;



/**

 * Workflow action: launch Cursor run and atomically bind to lifecycle context.

 * Bind/state: project, codeChange, channelId, contextId, __event, __sessionKey (from step args).

 * Returns a spread map ({@code launchMessage}, {@code launchSucceeded}) when used with {@code storeSpread: true}.

 */

public final class LaunchCursorRunAction implements com.vinekeepers.workflow.WorkflowAction {



    private static final Logger log = LoggerFactory.getLogger(LaunchCursorRunAction.class);

    private static final String DEFAULT_BASE_BRANCH = "main";



    private final CursorCloudAdapter adapter;

    private final StateStore stateStore;

    private final LifecycleContextStore lifecycleContextStore;

    private final FeaturePlanStateStore featurePlanStateStore;



    public LaunchCursorRunAction(CursorCloudAdapter adapter, StateStore stateStore,

                                 LifecycleContextStore lifecycleContextStore) {

        this(adapter, stateStore, lifecycleContextStore, null);

    }



    public LaunchCursorRunAction(CursorCloudAdapter adapter, StateStore stateStore,

                                 LifecycleContextStore lifecycleContextStore,

                                 FeaturePlanStateStore featurePlanStateStore) {

        this.adapter = adapter;

        this.stateStore = stateStore;

        this.lifecycleContextStore = lifecycleContextStore;

        this.featurePlanStateStore = featurePlanStateStore;

    }



    @Override

    public Object run(Event event, Map<String, Object> state, Map<String, Object> bind) {

        if (adapter == null || stateStore == null || lifecycleContextStore == null) {

            return failSpread("Launch cursor run dependencies not available.");

        }

        Map<String, Object> args = bind != null && !bind.isEmpty() ? bind : state;

        String project = getString(args, "project");

        String change = getString(args, "codeChange");

        if (change == null || change.isBlank()) {

            change = getString(args, "changeDescription");

        }

        if (project == null || project.isBlank()) {

            return failSpread("Missing project in state.");

        }

        if (change == null || change.isBlank()) {

            return failSpread("Missing feature request in state.");

        }

        String channelId = getString(args, "channelId");

        if (channelId == null || channelId.isBlank()) {

            Map<String, Object> eventMeta = getMap(args, "__event");

            channelId = getString(eventMeta, "channelId");

        }

        String contextId = getString(args, "contextId");

        String sessionKey = getString(args, "__sessionKey");

        if (sessionKey == null || sessionKey.isBlank()) {

            return failSpread("Missing workflow session key.");

        }



        if (!launchApprovalGateSkipped()

                && featurePlanStateStore != null

                && contextId != null

                && !contextId.isBlank()) {

            PlanApproval ap = featurePlanStateStore

                    .getByContextId(contextId)

                    .map(FeaturePlanState::getPlanApproval)

                    .orElse(null);

            if (ap == null || !PlanApprovalStatus.allowsLaunch(ap.getStatus())) {

                return failSpread("Plan approval required before launch (contextId: " + contextId + ").");

            }

        }



        String repositoryUrl = normalizeRepository(project);

        if (repositoryUrl == null) {

            return failSpread("Could not resolve project.");

        }

        String baseBranch = firstNonBlank(getString(args, "baseBranch"), Env.get("CURSOR_BASE_BRANCH", DEFAULT_BASE_BRANCH));

        String branchName = buildBranchName(change);

        Map<String, Object> eventMeta = getMap(args, "__event");

        String replyToMessageId = eventMeta != null ? getString(eventMeta, "messageId") : null;

        String deliveryChannelId = getString(args, "deliveryChannelId");



        CursorAgentLaunchRequest request = new CursorAgentLaunchRequest(

                CursorInstructionComposer.buildInstruction(repositoryUrl, baseBranch, change),

                repositoryUrl,

                baseBranch,

                branchName,

                true,

                Env.get("CURSOR_MODEL", "")

        );



        log.info("Cursor launch start: repo={}, sessionKey={}", repositoryUrl, sessionKey);

        try {

            CursorAgentLaunchResult launch = adapter.launchAgent(request);

            LifecycleRunRecord record = new LifecycleRunRecord(

                    launch.id(),

                    sessionKey,

                    project,

                    repositoryUrl,

                    firstNonBlank(launch.baseRef(), baseBranch),

                    firstNonBlank(launch.branchName(), branchName),

                    launch.agentUrl(),

                    change,

                    channelId,

                    replyToMessageId,

                    deliveryChannelId,

                    firstNonBlankInstant(launch.createdAt(), Instant.now()),

                    launch.status()

            );

            stateStore.put(runKey(launch.id()), record);

            stateStore.put(sessionRunKey(sessionKey), launch.id());

            String authorId = eventMeta != null ? getString(eventMeta, "authorId") : null;

            if (authorId != null && !authorId.isBlank()) {

                stateStore.put("luna:lastRepo:" + authorId, project);

            }

            if (contextId != null && !contextId.isBlank()) {

                lifecycleContextStore.bindExternalRunId(contextId, launch.id());

                lifecycleContextStore.getByContextId(contextId).ifPresent(ctx -> ctx.setStatus("active"));

            }

            String ack = buildAcknowledgement(record);

            log.info("Cursor launch success: agentId={}, status={}", launch.id(), launch.status());

            return successSpread(ack, launch.id());

        } catch (CursorCloudException e) {

            String msg = "Cursor launch failed: " + e.getMessage();

            log.warn("Cursor launch failed: {}", e.getMessage());

            return failSpread(msg);

        }

    }



    private static Map<String, Object> successSpread(String launchMessage, String agentId) {

        Map<String, Object> m = new LinkedHashMap<>();

        m.put("launchMessage", launchMessage);

        m.put("launchSucceeded", "true");

        m.put("launchAgentId", agentId != null ? agentId : "");

        return m;

    }



    private static Map<String, Object> failSpread(String launchMessage) {

        Map<String, Object> m = new LinkedHashMap<>();

        m.put("launchMessage", launchMessage);

        m.put("launchSucceeded", "false");

        m.put("launchAgentId", "");

        return m;

    }



    private static boolean launchApprovalGateSkipped() {

        String v = Env.get("CURSOR_LAUNCH_SKIP_APPROVAL_GATE", "");

        return "true".equalsIgnoreCase(v != null ? v.trim() : "");

    }



    private static String runKey(String agentId) {

        return "cursor:run:" + agentId;

    }



    private static String sessionRunKey(String sessionKey) {

        return "cursor:session:" + sessionKey + ":lastRun";

    }



    private static String buildBranchName(String change) {

        String normalized = change.toLowerCase(Locale.ROOT)

                .replaceAll("[^a-z0-9]+", "-")

                .replaceAll("(^-|-$)", "");

        if (normalized.isBlank()) {

            normalized = "request";

        }

        if (normalized.length() > 32) {

            normalized = normalized.substring(0, 32);

        }

        return "luna/" + normalized + "-" + System.currentTimeMillis();

    }



    private static String buildAcknowledgement(LifecycleRunRecord record) {

        StringBuilder message = new StringBuilder("Launching Cursor Cloud run for `")

                .append(record.getRepositoryUrl())

                .append("` on branch `")

                .append(record.getBranchName())

                .append("`.");

        if (record.getAgentUrl() != null && !record.getAgentUrl().isBlank()) {

            message.append(" Agent: ").append(record.getAgentUrl());

        }

        message.append(" Status: launching. Updates will appear in your dedicated lifecycle room.");

        return message.toString();

    }



    private static String getString(Map<String, Object> map, String key) {

        if (map == null) {

            return null;

        }

        Object v = map.get(key);

        return v != null ? v.toString() : null;

    }



    @SuppressWarnings("unchecked")

    private static Map<String, Object> getMap(Map<String, Object> map, String key) {

        if (map == null) {

            return Map.of();

        }

        Object v = map.get(key);

        if (v instanceof Map<?, ?> m) {

            return (Map<String, Object>) m;

        }

        return Map.of();

    }



    private static String firstNonBlank(String a, String b) {

        return a != null && !a.isBlank() ? a : b;

    }



    private static Instant firstNonBlankInstant(Instant a, Instant b) {

        return a != null ? a : b;

    }



    private static long parseTimeoutMs(String raw, long fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            long v = Long.parseLong(raw.trim());
            return v > 0 ? v : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String normalizeRepository(String project) {

        if (project == null || project.isBlank()) {

            return null;

        }

        String t = project.trim();

        if (t.startsWith("https://github.com/")) {

            return t;

        }

        if (t.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) {

            return "https://github.com/" + t;

        }

        return null;

    }

}

