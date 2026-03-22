package com.vinekeepers.workflow.planning;

import com.vinekeepers.state.workflow.UnresolvedItem;
import com.vinekeepers.state.workflow.UnresolvedItemLedger;
import com.vinekeepers.state.workflow.UnresolvedItemStatus;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Keeps {@link UnresolvedItemLedger} in sync with planning clarification ranking and merge (dual-write with
 * {@link com.vinekeepers.state.planning.FeaturePlanState}).
 */
public final class PlanningDeliberationLedgerSync {

    private PlanningDeliberationLedgerSync() {}

    public record UpsertResult(UnresolvedItemLedger ledger, Optional<String> activeItemId) {}

    /**
     * When a clarification is required, upsert an OPEN item (single active planning question) and expose its id for
     * merge correlation.
     */
    public static UpsertResult upsertOpenQuestion(UnresolvedItemLedger ledger, PlanningQuestionRankingPolicy.RankedClarification ranked) {
        UnresolvedItemLedger base = ledger != null ? ledger : UnresolvedItemLedger.empty();
        if (!ranked.userInputRequired()) {
            return new UpsertResult(base, Optional.empty());
        }
        String q = ranked.questionText() != null ? ranked.questionText().trim() : "";
        if (q.isBlank()) {
            return new UpsertResult(base, Optional.empty());
        }
        if (base.hasFingerprintMergeClosed(q)) {
            return new UpsertResult(base, Optional.empty());
        }
        String fp = UnresolvedItemLedger.normalizeFingerprint(q);
        String inputKind = ranked.useStructuredChoices() ? "bounded_choice" : "open";
        Map<String, String> source = new LinkedHashMap<>();
        source.put("channel", "planning_clarification");
        source.put("inputKind", inputKind);
        source.put("lastAskedAt", String.valueOf(System.currentTimeMillis()));
        String severity = ranked.blockingQuestionCount() > 0 ? "blocking" : "normal";

        Optional<UnresolvedItem> active = base.findActiveByFingerprint(fp);
        if (active.isPresent()) {
            UnresolvedItem it = active.get();
            UnresolvedItem next =
                    it.withIncrementRepeatCount()
                            .withMergedSource(source)
                            .withStatus(UnresolvedItemStatus.OPEN);
            return new UpsertResult(base.withReplaced(it.getId(), next), Optional.of(it.getId()));
        }

        UnresolvedItemLedger withoutOthers = base.withCancelledOpenPlanningExcept("");
        String id = UnresolvedItemLedger.newId();
        UnresolvedItem created =
                new UnresolvedItem(
                        id,
                        fp,
                        UnresolvedItemStatus.OPEN,
                        "",
                        q,
                        severity,
                        source,
                        java.util.List.of(),
                        java.util.List.of(),
                        0);
        return new UpsertResult(withoutOthers.withAdded(created), Optional.of(id));
    }

    /**
     * Mark the active planning clarification as merged with the user's answer (authoritative closure for fingerprint).
     */
    public static UnresolvedItemLedger mergeAnswerIntoLedger(
            UnresolvedItemLedger ledger,
            String ledgerItemId,
            String questionFingerprint,
            String rawAnswer,
            String normalizedAnswer) {
        UnresolvedItemLedger base = ledger != null ? ledger : UnresolvedItemLedger.empty();
        if (ledgerItemId != null && !ledgerItemId.isBlank()) {
            for (UnresolvedItem it : base.items()) {
                if (it.getId().equals(ledgerItemId.trim())) {
                    return replaceMerged(base, it, rawAnswer, normalizedAnswer);
                }
            }
        }
        if (questionFingerprint != null && !questionFingerprint.isBlank()) {
            String norm = UnresolvedItemLedger.normalizeFingerprint(questionFingerprint);
            Optional<UnresolvedItem> active = base.findActiveByFingerprint(norm);
            if (active.isPresent()) {
                return replaceMerged(base, active.get(), rawAnswer, normalizedAnswer);
            }
            for (UnresolvedItem it : base.items()) {
                if (norm.equals(it.getFingerprint())
                        && (it.getStatus() == UnresolvedItemStatus.OPEN
                                || it.getStatus() == UnresolvedItemStatus.ANSWERED
                                || it.getStatus() == UnresolvedItemStatus.BLOCKED)) {
                    return replaceMerged(base, it, rawAnswer, normalizedAnswer);
                }
            }
        }
        return base;
    }

    private static UnresolvedItemLedger replaceMerged(
            UnresolvedItemLedger base, UnresolvedItem it, String rawAnswer, String normalizedAnswer) {
        UnresolvedItem next =
                it.withAppendedAnswer(rawAnswer, normalizedAnswer)
                        .withStatus(UnresolvedItemStatus.MERGED)
                        .withMergedSource(
                                Map.of(
                                        "mergedAt",
                                        String.valueOf(System.currentTimeMillis()),
                                        "resolutionNotes",
                                        "User clarification applied to plan."));
        return base.withReplaced(it.getId(), next);
    }
}
