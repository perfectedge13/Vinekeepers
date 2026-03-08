package com.vinekeepers.core.cursor;

import java.time.Instant;

/**
 * Launch response from the Cursor cloud agent API.
 */
public record CursorAgentLaunchResult(
        String id,
        String name,
        String status,
        String repositoryUrl,
        String baseRef,
        String branchName,
        String agentUrl,
        String prUrl,
        boolean autoCreatePr,
        Instant createdAt
) {
}
