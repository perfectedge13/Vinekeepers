package com.vinekeepers.core.cursor;

import java.util.Objects;

/**
 * Single source of truth for the Cursor run instruction text.
 * Builds the full prompt including /nova-code and spec-driven workflow requirements.
 * Used by {@link com.vinekeepers.tools.CursorFullRunTool} and
 * {@link com.vinekeepers.workflow.actions.LaunchCursorRunAction}.
 */
public final class CursorInstructionComposer {

    private static final String NOVA_CODE_PREFIX = "/nova-code";

    /**
     * Builds the instruction string for a Cursor agent run from repository, base branch, and change description.
     *
     * @param repositoryUrl full repository URL (e.g. https://github.com/owner/repo)
     * @param baseBranch    base branch name (e.g. main)
     * @param changeDescription user request or feature description
     * @return full prompt including /nova-code and requirements
     */
    public static String buildInstruction(String repositoryUrl, String baseBranch, String changeDescription) {
        Objects.requireNonNull(repositoryUrl, "repositoryUrl");
        String base = baseBranch != null && !baseBranch.isBlank() ? baseBranch : "main";
        String change = changeDescription != null && !changeDescription.isBlank() ? changeDescription : "";

        return """
                %s

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
                """.formatted(NOVA_CODE_PREFIX, repositoryUrl, base, change);
    }
}
