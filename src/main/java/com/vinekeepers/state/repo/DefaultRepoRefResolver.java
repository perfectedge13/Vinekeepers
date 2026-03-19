package com.vinekeepers.state.repo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Normalizes https/git URLs, owner/repo shorthand, and existing local directory paths.
 */
public final class DefaultRepoRefResolver implements RepoRefResolver {

    private static final Pattern OWNER_REPO = Pattern.compile("^[a-zA-Z0-9_.-]+/[a-zA-Z0-9_.-]+$");

    @Override
    public String resolve(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return null;
        }
        if (s.startsWith("https://") || s.startsWith("http://")) {
            String lower = s.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".git")) {
                return s.substring(0, s.length() - 4);
            }
            return s;
        }
        if (s.startsWith("git@")) {
            return s;
        }
        if (OWNER_REPO.matcher(s).matches()) {
            return s.toLowerCase(Locale.ROOT);
        }
        try {
            Path p = Path.of(s);
            if (p.isAbsolute() && Files.isDirectory(p)) {
                return "local:" + p.normalize().toAbsolutePath().toString().replace('\\', '/');
            }
        } catch (Exception ignored) {
            // not a valid path
        }
        return s;
    }
}
