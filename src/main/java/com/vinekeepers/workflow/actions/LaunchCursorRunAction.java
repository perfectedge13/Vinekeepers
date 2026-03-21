package com.vinekeepers.workflow.actions;

import com.vinekeepers.core.cursor.CursorAgentLaunchRequest;
import com.vinekeepers.core.cursor.CursorAgentLaunchResult;
import com.vinekeepers.core.cursor.CursorCloudAdapter;
import com.vinekeepers.core.cursor.CursorCloudException;
import com.vinekeepers.core.cursor.CursorInstructionComposer;
import com.vinekeepers.core.cursor.CursorLaunchModel;
import com.vinekeepers.core.cursor.LifecycleRunRecord;
import com.vinekeepers.env.Env;
import com.vinekeepers.events.Event;
import com.vinekeepers.state.LifecycleContextStore;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;

import com.vinekeepers.state.StateStore;

/**
 * Workflow action: launch Cursor run and atomically bind to lifecycle context.
 * Bind/state: project, codeChange, channelId, contextId, __event, __sessionKey (from step args).
 */
public final class LaunchCursorRunAction implements com.vinekeepers.workflow.WorkflowAction {

    private static final String DEFAULT_BASE_BRANCH = "main";

    private final CursorCloudAdapter adapter;
    private final StateStore stateStore;
    private final LifecycleContextStore lifecycleContextStore;

    public LaunchCursorRunAction(CursorCloudAdapter adapter, StateStore stateStore,
                                 LifecycleContextStore lifecycleContextStore) {
        this.adapter = adapter;
        this.stateStore = stateStore;
        this.lifecycleContextStore = lifecycleContextStore;
    }

    @Override
    public Object run(Event event, Map<String, Object> state, Map<String, Object> bind) {
        if (adapter == null || stateStore == null || lifecycleContextStore == null) {
            return "Launch cursor run dependencies not available.";
        }
        Map<String, Object> args = bind != null && !bind.isEmpty() ? bind : state;
        String project = getString(args, "project");
        String change = getString(args, "codeChange");
        if (change == null || change.isBlank()) {
            change = getString(args, "changeDescription");
        }
        if (project == null || project.isBlank()) {
            return "Missing project in state.";
        }
        if (change == null || change.isBlank()) {
            return "Missing feature request in state.";
        }
        String channelId = getString(args, "channelId");
        if (channelId == null || channelId.isBlank()) {
            Map<String, Object> eventMeta = getMap(args, "__event");
            channelId = getString(eventMeta, "channelId");
        }
        String contextId = getString(args, "contextId");
        String sessionKey = getString(args, "__sessionKey");
        if (sessionKey == null || sessionKey.isBlank()) {
            return "Missing workflow session key.";
        }

        String repositoryUrl = normalizeRepository(project);
        if (repositoryUrl == null) {
            return "Could not resolve project.";
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
                CursorLaunchModel.resolveForLaunch(args)
        );

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
            return buildAcknowledgement(record);
        } catch (CursorCloudException e) {
            return "Cursor launch failed: " + e.getMessage();
        }
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
        if (normalized.isBlank()) normalized = "request";
        if (normalized.length() > 32) normalized = normalized.substring(0, 32);
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
        if (map == null) return null;
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getMap(Map<String, Object> map, String key) {
        if (map == null) return Map.of();
        Object v = map.get(key);
        if (v instanceof Map<?, ?> m) return (Map<String, Object>) m;
        return Map.of();
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }

    private static Instant firstNonBlankInstant(Instant a, Instant b) {
        return a != null ? a : b;
    }

    private static String normalizeRepository(String project) {
        if (project == null || project.isBlank()) return null;
        String t = project.trim();
        if (t.startsWith("https://github.com/")) return t;
        if (t.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) return "https://github.com/" + t;
        return null;
    }
}
