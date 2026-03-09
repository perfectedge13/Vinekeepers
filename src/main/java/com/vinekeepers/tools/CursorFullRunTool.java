package com.vinekeepers.tools;

import com.vinekeepers.core.cursor.CursorAgentLaunchRequest;
import com.vinekeepers.core.cursor.CursorAgentLaunchResult;
import com.vinekeepers.core.cursor.CursorCloudAdapter;
import com.vinekeepers.core.cursor.CursorCloudAdapterImpl;
import com.vinekeepers.core.cursor.CursorCloudException;
import com.vinekeepers.core.cursor.LunaCloudRunState;
import com.vinekeepers.env.Env;
import com.vinekeepers.state.StateStore;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Tool wrapper that launches a Cursor cloud agent run for Luna.
 */
public final class CursorFullRunTool implements Tool {

    public static final String ID = "cursor.fullRun";
    private static final String DEFAULT_BASE_BRANCH = "main";

    private final CursorCloudAdapter adapter;
    private final StateStore stateStore;

    public CursorFullRunTool() {
        this(new CursorCloudAdapterImpl(), new StateStore());
    }

    public CursorFullRunTool(CursorCloudAdapter adapter, StateStore stateStore) {
        this.adapter = adapter != null ? adapter : new CursorCloudAdapterImpl();
        this.stateStore = Objects.requireNonNull(stateStore, "stateStore");
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public Object run(Map<String, Object> args) {
        String project = getString(args, "project");
        String change = getString(args, "codeChange");
        if (change == null || change.isBlank()) {
            change = getString(args, "changeDescription");
        }
        if (project == null || project.isBlank()) {
            return "Missing project in state. Reply with a GitHub repository URL or owner/repo.";
        }
        if (change == null || change.isBlank()) {
            return "Missing feature request in state. Describe the feature or code change to implement.";
        }

        String repositoryUrl = normalizeRepository(project);
        if (repositoryUrl == null) {
            return "Could not resolve project `" + project + "`. Reply with a full GitHub repository URL or `owner/repo`.";
        }

        String sessionKey = getString(args, "__sessionKey");
        if (sessionKey == null || sessionKey.isBlank()) {
            return "Missing workflow session key. Cursor launch aborted.";
        }

        Map<String, Object> eventMetadata = getMap(args, "__event");
        String baseBranch = firstNonBlank(getString(args, "baseBranch"), Env.get("CURSOR_BASE_BRANCH", DEFAULT_BASE_BRANCH));
        String branchName = buildBranchName(change);
        CursorAgentLaunchRequest request = new CursorAgentLaunchRequest(
                buildPrompt(repositoryUrl, baseBranch, change),
                repositoryUrl,
                baseBranch,
                branchName,
                true,
                Env.get("CURSOR_MODEL", "")
        );

        try {
            CursorAgentLaunchResult launch = adapter.launchAgent(request);
            LunaCloudRunState runState = new LunaCloudRunState(
                    launch.id(),
                    sessionKey,
                    project,
                    repositoryUrl,
                    firstNonBlank(launch.baseRef(), baseBranch),
                    firstNonBlank(launch.branchName(), branchName),
                    launch.agentUrl(),
                    change,
                    getString(eventMetadata, "channelId"),
                    getString(eventMetadata, "messageId"),
                    firstNonBlankInstant(launch.createdAt(), Instant.now()),
                    launch.status()
            );
            stateStore.put(runKey(launch.id()), runState);
            stateStore.put(sessionRunKey(sessionKey), launch.id());
            String authorId = getString(eventMetadata, "authorId");
            if (authorId != null && !authorId.isBlank()) {
                stateStore.put("luna:lastRepo:" + authorId, project);
            }
            return buildLaunchAcknowledgement(runState);
        } catch (CursorCloudException e) {
            return "Cursor launch failed: " + e.getMessage();
        }
    }

    private static String getString(Map<String, Object> map, String key) {
        if (map == null) {
            return null;
        }
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getMap(Map<String, Object> map, String key) {
        if (map == null) {
            return Map.of();
        }
        Object value = map.get(key);
        if (value instanceof Map<?, ?> nested) {
            return (Map<String, Object>) nested;
        }
        return Map.of();
    }

    private static String normalizeRepository(String project) {
        if (project == null || project.isBlank()) {
            return null;
        }
        String trimmed = project.trim();
        if (trimmed.startsWith("https://github.com/")) {
            return trimmed;
        }
        if (trimmed.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) {
            return "https://github.com/" + trimmed;
        }
        return null;
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

    private static String buildPrompt(String repositoryUrl, String baseBranch, String change) {
        return """
                Implement the following feature request in the repository.

                Repository: %s
                Base branch: %s

                User request:
                %s

                Requirements:
                - Follow the repository's Cursor rules and spec-driven workflow.
                - Update specs, tests, README, and mkdoc content when behavior changes.
                - Run the repository validation commands before finishing.
                - Work on the feature branch created for this run and open a pull request.
                - Summarize the final change set, tests, and any remaining issues.
                """.formatted(repositoryUrl, baseBranch, change);
    }

    private static String buildLaunchAcknowledgement(LunaCloudRunState runState) {
        StringBuilder message = new StringBuilder("Launching Cursor Cloud run for `")
                .append(runState.getRepositoryUrl())
                .append("` on branch `")
                .append(runState.getBranchName())
                .append("`.");
        if (runState.getAgentUrl() != null && !runState.getAgentUrl().isBlank()) {
            message.append(" Agent: ").append(runState.getAgentUrl());
        }
        message.append(" I will post progress updates here as Cursor works.");
        return message.toString();
    }

    private static String runKey(String agentId) {
        return "cursor:run:" + agentId;
    }

    private static String sessionRunKey(String sessionKey) {
        return "cursor:session:" + sessionKey + ":lastRun";
    }

    private static String firstNonBlank(String preferred, String fallback) {
        return preferred != null && !preferred.isBlank() ? preferred : fallback;
    }

    private static Instant firstNonBlankInstant(Instant preferred, Instant fallback) {
        return preferred != null ? preferred : fallback;
    }
}
