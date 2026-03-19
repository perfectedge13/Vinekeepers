package com.vinekeepers.state.repo;

/**
 * Normalizes user/workflow repo input into a canonical ref string for indexing and workspace logic.
 */
public interface RepoRefResolver {

    /**
     * @param raw user input (trimmed internally); may be null
     * @return canonical ref, or null if input is unusable
     */
    String resolve(String raw);
}
