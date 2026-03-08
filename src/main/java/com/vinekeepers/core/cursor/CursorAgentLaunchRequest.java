package com.vinekeepers.core.cursor;

/**
 * Parameters required to launch a Cursor cloud agent run.
 */
public record CursorAgentLaunchRequest(
        String promptText,
        String repositoryUrl,
        String baseRef,
        String branchName,
        boolean autoCreatePr,
        String model
) {
}
