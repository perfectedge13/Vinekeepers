package com.vinekeepers.profile;

import java.nio.file.Path;

/**
 * Loads the repo's work-profiles.yaml for tests (Maven cwd = project root).
 */
public final class TestWorkProfiles {

    private TestWorkProfiles() {}

    public static WorkProfileRegistry loadFromRepoConfig() {
        return WorkProfileLoader.load(Path.of("config", "work-profiles.yaml"));
    }
}
