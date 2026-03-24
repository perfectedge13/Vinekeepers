package com.vinekeepers.workflow.planning;

import com.vinekeepers.state.workflow.ProgressDedupeHelper;
import com.vinekeepers.state.workflow.ProgressEventLog;
import com.vinekeepers.state.workflow.UnresolvedItem;
import com.vinekeepers.state.workflow.UnresolvedItemLedger;
import com.vinekeepers.state.workflow.UnresolvedItemStatus;

import java.util.LinkedHashMap;
import java.util.Map;

final class PlanningProgressSpreadSupport {

    private PlanningProgressSpreadSupport() {}

    static void enrichUserCopyAndProgressLog(Map<String, Object> state, Map<String, Object> spread) {
        UnresolvedItemLedger ledger = UnresolvedItemLedger.readFrom(spread);
        StringBuilder blockers = new StringBuilder();
        for (UnresolvedItem it : ledger.items()) {
            UnresolvedItemStatus status = it.getStatus();
            if (status == UnresolvedItemStatus.OPEN || status == UnresolvedItemStatus.BLOCKED) {
                String question = it.getQuestionText();
                if (question != null && !question.isBlank()) {
                    if (blockers.length() > 0) {
                        blockers.append("; ");
                    }
                    blockers.append(question.trim());
                }
            }
        }
        spread.put("userCopyBlockerSummary", blockers.toString());
        String ledgerItemId = getString(spread, "planningClarificationLedgerItemId");
        spread.put("userCopyClarificationLedgerId", ledgerItemId != null ? ledgerItemId : "");

        String delta =
                firstNonBlank(
                        getString(spread, "planningSelectiveRerunNote"),
                        truncateOneLine(getString(spread, "planningCycleProgressSummary"), 220));
        spread.put("userCopyProgressDelta", delta != null ? delta : "");

        ProgressEventLog log = ProgressEventLog.readFrom(state);
        String line =
                firstNonBlank(
                        getString(spread, "planningSelectiveRerunNote"),
                        truncateOneLine(getString(spread, "planningCycleProgressSummary"), 300));
        if (line != null && !line.isBlank()) {
            Map<String, String> refs = new LinkedHashMap<>();
            String deliberationPhase = getString(spread, "deliberationPhase");
            if (deliberationPhase != null && !deliberationPhase.isBlank()) {
                refs.put("phase", deliberationPhase);
            }
            log = log.withAppendedTyped("pass_status", line, "info", refs);
        }
        ProgressEventLog.mergeIntoSpread(spread, log);
        spread.put("userCopyProgressLine", log.latestMessage());
    }

    static void finishProgressFingerprint(Map<String, Object> state, Map<String, Object> spread) {
        String summary =
                spread.get("planningCycleProgressSummary") != null
                        ? spread.get("planningCycleProgressSummary").toString()
                        : "";
        String progressBody = firstNonBlank(getString(spread, "userCopyCoordinatorProgress"), summary);
        if (progressBody == null) {
            progressBody = "";
        }
        String fullForHash = ProgressDedupeHelper.normalizeProgressBody("**Update:** " + progressBody);
        String fingerprint = ProgressDedupeHelper.fingerprintForBody(fullForHash);
        spread.put("planningProgressPostFingerprint", fingerprint);
        String last = state != null ? getString(state, "planningLastProgressPostHash") : null;
        boolean worthy = ProgressDedupeHelper.isPostWorthy(last, fingerprint);
        spread.put("planningProgressPostWorthy", worthy ? "true" : "false");
    }

    static String truncateOneLine(String value, int max) {
        if (value == null) {
            return "";
        }
        String normalized = value.replace("\r\n", " ").replace("\n", " ").trim();
        return normalized.length() <= max ? normalized : normalized.substring(0, max - 1) + "…";
    }

    static String getString(Map<String, ?> map, String key) {
        if (map == null) {
            return null;
        }
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    static String firstNonBlank(String primary, String fallback) {
        return primary != null && !primary.isBlank()
                ? primary
                : (fallback != null && !fallback.isBlank() ? fallback : null);
    }
}
