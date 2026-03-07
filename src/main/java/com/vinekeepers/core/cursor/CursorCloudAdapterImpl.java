package com.vinekeepers.core.cursor;

import com.vinekeepers.env.Env;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cursor Cloud API adapter implementation.
 * Reads API key and optional base URL from environment (CURSOR_API_KEY, CURSOR_API_BASE_URL).
 * Stub implementation: logs and returns placeholder results until real API integration.
 */
public final class CursorCloudAdapterImpl implements CursorCloudAdapter {

    private static final Logger log = LoggerFactory.getLogger(CursorCloudAdapterImpl.class);

    private final String apiKey;
    private final String baseUrl;

    public CursorCloudAdapterImpl() {
        this.apiKey = Env.get("CURSOR_API_KEY", "");
        this.baseUrl = Env.get("CURSOR_API_BASE_URL", "https://api.cursor.com");
    }

    @Override
    public String createBranch(String projectPathOrId, String branchName) {
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("CURSOR_API_KEY not set; createBranch stub");
            return "stub: branch " + (branchName != null ? branchName : "luna-feature") + " (no API key)";
        }
        log.info("Cursor createBranch: project={} branch={}", projectPathOrId, branchName);
        return "Branch created: " + (branchName != null ? branchName : "luna-feature");
    }

    @Override
    public String runNovaCommit(String projectPathOrId, String changeDescription) {
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("CURSOR_API_KEY not set; runNovaCommit stub");
            return "stub: nova-commit (no API key)";
        }
        log.info("Cursor runNovaCommit: project={} change={}", projectPathOrId, changeDescription != null ? changeDescription.substring(0, Math.min(100, changeDescription.length())) : "");
        return "nova-commit completed";
    }

    @Override
    public String push(String projectPathOrId) {
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("CURSOR_API_KEY not set; push stub");
            return "stub: push (no API key)";
        }
        log.info("Cursor push: project={}", projectPathOrId);
        return "Pushed to GitHub";
    }

    @Override
    public String createPr(String projectPathOrId, String title) {
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("CURSOR_API_KEY not set; createPr stub");
            return "stub: PR (no API key)";
        }
        log.info("Cursor createPr: project={} title={}", projectPathOrId, title);
        return "PR created: " + (title != null ? title : "Luna change");
    }
}
