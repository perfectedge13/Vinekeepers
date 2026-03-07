package com.vinekeepers.core.cursor;

/**
 * Adapter for Cursor Cloud API: create feature branch, run nova-code/nova-commit,
 * push to GitHub, optionally create PR.
 * Configuration (e.g. API key, base URL) is read from environment/config.
 */
public interface CursorCloudAdapter {

    /**
     * Create a feature branch for the given project.
     * @return branch name or error message
     */
    String createBranch(String projectPathOrId, String branchName);

    /**
     * Run nova-commit (or nova-code) for the given project with the given change description.
     * @return success message or error
     */
    String runNovaCommit(String projectPathOrId, String changeDescription);

    /**
     * Push the current branch to GitHub.
     * @return success message or error
     */
    String push(String projectPathOrId);

    /**
     * Create a pull request (optional).
     * @return PR URL or message, or error
     */
    String createPr(String projectPathOrId, String title);
}
