package com.vinekeepers.core.cursor;

import java.time.Instant;

/**
 * Current state of a Cursor cloud agent run.
 */
public record CursorAgentDetails(
        String id,
        String name,
        String status,
        String repositoryUrl,
        String baseRef,
        String branchName,
        String agentUrl,
        String prUrl,
        String summary,
        Instant createdAt
) {
}
