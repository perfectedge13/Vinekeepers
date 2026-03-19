package com.vinekeepers.state.repo;

/**
 * How the workspace was obtained.
 */
public enum RepoMaterializationMode {
    EXISTING_LOCAL,
    CLONED,
    CONNECTED_SOURCE,
    UNKNOWN
}
