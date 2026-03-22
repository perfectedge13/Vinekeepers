package com.vinekeepers.core.cursor;

import java.time.Instant;

/**
 * Generic in-memory record for a lifecycle Cursor cloud agent run (channel, reply target, remote status).
 * Indexed by channelId and externalRunId (agentId).
 */
public final class LifecycleRunRecord {

    private final String agentId;
    private final String sessionKey;
    private final String projectInput;
    private final String repositoryUrl;
    private final String baseRef;
    private final String branchName;
    private final String agentUrl;
    private final String changeRequest;
    private final String channelId;
    private final String replyToMessageId;
    private final String deliveryChannelId;
    private final Instant launchedAt;

    private String status;
    private String prUrl;
    private String summary;
    private String lastAssistantMessageId;
    private String lastAssistantMessage;
    private Instant lastPolledAt;
    private Instant completedAt;
    private boolean terminalNotificationSent;
    /** Last Discord line posted for status / assistant / PR (dedupe repeated polls). */
    private String lastPostedStatusLine;
    private String lastPostedAssistantLine;
    private String lastPostedPrLine;

    public LifecycleRunRecord(String agentId, String sessionKey, String projectInput,
                             String repositoryUrl, String baseRef, String branchName,
                             String agentUrl, String changeRequest, String channelId,
                             String replyToMessageId, Instant launchedAt, String status) {
        this(agentId, sessionKey, projectInput, repositoryUrl, baseRef, branchName, agentUrl, changeRequest, channelId, replyToMessageId, null, launchedAt, status);
    }

    public LifecycleRunRecord(String agentId, String sessionKey, String projectInput,
                             String repositoryUrl, String baseRef, String branchName,
                             String agentUrl, String changeRequest, String channelId,
                             String replyToMessageId, String deliveryChannelId, Instant launchedAt, String status) {
        this.agentId = agentId;
        this.sessionKey = sessionKey;
        this.projectInput = projectInput;
        this.repositoryUrl = repositoryUrl;
        this.baseRef = baseRef;
        this.branchName = branchName;
        this.agentUrl = agentUrl;
        this.changeRequest = changeRequest;
        this.channelId = channelId;
        this.replyToMessageId = replyToMessageId;
        this.deliveryChannelId = deliveryChannelId;
        this.launchedAt = launchedAt;
        this.status = status;
    }

    public synchronized String getAgentId() {
        return agentId;
    }

    public synchronized String getSessionKey() {
        return sessionKey;
    }

    public synchronized String getProjectInput() {
        return projectInput;
    }

    public synchronized String getRepositoryUrl() {
        return repositoryUrl;
    }

    public synchronized String getBaseRef() {
        return baseRef;
    }

    public synchronized String getBranchName() {
        return branchName;
    }

    public synchronized String getAgentUrl() {
        return agentUrl;
    }

    public synchronized String getChangeRequest() {
        return changeRequest;
    }

    public synchronized String getChannelId() {
        return channelId;
    }

    public synchronized String getReplyToMessageId() {
        return replyToMessageId;
    }

    public synchronized String getDeliveryChannelId() {
        return deliveryChannelId;
    }

    public synchronized Instant getLaunchedAt() {
        return launchedAt;
    }

    public synchronized String getStatus() {
        return status;
    }

    public synchronized String getPrUrl() {
        return prUrl;
    }

    public synchronized String getSummary() {
        return summary;
    }

    public synchronized String getLastAssistantMessageId() {
        return lastAssistantMessageId;
    }

    public synchronized String getLastAssistantMessage() {
        return lastAssistantMessage;
    }

    public synchronized Instant getLastPolledAt() {
        return lastPolledAt;
    }

    public synchronized Instant getCompletedAt() {
        return completedAt;
    }

    public synchronized boolean isTerminalNotificationSent() {
        return terminalNotificationSent;
    }

    public synchronized void applyAgentDetails(CursorAgentDetails details) {
        if (details == null) {
            return;
        }
        this.status = details.status();
        this.prUrl = details.prUrl();
        this.summary = details.summary();
        this.lastPolledAt = Instant.now();
        if (isTerminalStatus(details.status()) && this.completedAt == null) {
            this.completedAt = Instant.now();
        }
    }

    public synchronized void recordAssistantMessage(CursorAgentMessage message) {
        if (message == null) {
            return;
        }
        this.lastAssistantMessageId = message.id();
        this.lastAssistantMessage = message.text();
        this.lastPolledAt = Instant.now();
    }

    public synchronized void markTerminalNotificationSent() {
        this.terminalNotificationSent = true;
    }

    public synchronized boolean shouldPostStatusLine(String line) {
        String norm = normalizeDedupeLine(line);
        if (norm.isBlank()) {
            return false;
        }
        if (norm.equals(lastPostedStatusLine)) {
            return false;
        }
        this.lastPostedStatusLine = norm;
        return true;
    }

    public synchronized boolean shouldPostAssistantLine(String line) {
        String norm = normalizeDedupeLine(line);
        if (norm.isBlank()) {
            return false;
        }
        if (norm.equals(lastPostedAssistantLine)) {
            return false;
        }
        this.lastPostedAssistantLine = norm;
        return true;
    }

    public synchronized boolean shouldPostPrLine(String line) {
        String norm = normalizeDedupeLine(line);
        if (norm.isBlank()) {
            return false;
        }
        if (norm.equals(lastPostedPrLine)) {
            return false;
        }
        this.lastPostedPrLine = norm;
        return true;
    }

    private static String normalizeDedupeLine(String line) {
        if (line == null) {
            return "";
        }
        return line.trim().replaceAll("\\s+", " ");
    }

    public synchronized boolean isTerminal() {
        return isTerminalStatus(status);
    }

    public static boolean isTerminalStatus(String status) {
        if (status == null) {
            return false;
        }
        return "FINISHED".equalsIgnoreCase(status)
                || "ERROR".equalsIgnoreCase(status)
                || "EXPIRED".equalsIgnoreCase(status);
    }
}
