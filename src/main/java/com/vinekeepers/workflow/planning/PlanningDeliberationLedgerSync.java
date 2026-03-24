package com.vinekeepers.workflow.planning;

import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.state.planning.PlanningIntakeStage;
import com.vinekeepers.state.workflow.UnresolvedItem;
import com.vinekeepers.state.workflow.UnresolvedItemLedger;
import com.vinekeepers.state.workflow.UnresolvedItemStatus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Keeps {@link UnresolvedItemLedger} in sync with planning clarification projection and merge (dual-write with
 * {@link com.vinekeepers.state.planning.FeaturePlanState}).
 */
public final class PlanningDeliberationLedgerSync {

    public static final String PLANNING_CLARIFICATION_CHANNEL = "planning_clarification";

    private PlanningDeliberationLedgerSync() {}

    public record UpsertResult(UnresolvedItemLedger ledger, Optional<String> activeItemId) {}

    /**
     * When a clarification is required, upsert an OPEN item (single active planning question) and expose its id for
     * merge correlation.
     */
    public static UpsertResult upsertOpenQuestion(UnresolvedItemLedger ledger, ClarificationProjection ranked) {
        UnresolvedItemLedger base = ledger != null ? ledger : UnresolvedItemLedger.empty();
        if (!ranked.userInputRequired()) {
            return new UpsertResult(base, Optional.empty());
        }
        String q = ranked.questionText() != null ? ranked.questionText().trim() : "";
        if (q.isBlank()) {
            return new UpsertResult(base, Optional.empty());
        }
        if (mergedPlanningCoversSemanticallySimilarQuestion(base, q)) {
            return new UpsertResult(base, Optional.empty());
        }
        if (base.hasFingerprintMergeClosed(q)) {
            return new UpsertResult(base, Optional.empty());
        }
        String fp = UnresolvedItemLedger.normalizeFingerprint(q);
        String inputKind = ranked.useStructuredChoices() ? "bounded_choice" : "open";
        Map<String, String> source = new LinkedHashMap<>();
        source.put("channel", PLANNING_CLARIFICATION_CHANNEL);
        source.put("topicKey", fp);
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
     * Upsert when {@link com.vinekeepers.profile.CoordinatorClarificationMode#CANONICAL_V1} drives the question: correlate
     * by stable {@code gapId} instead of only question-text fingerprint.
     */
    public static UpsertResult upsertOpenQuestionForCanonicalGap(
            UnresolvedItemLedger ledger,
            ClarificationProjection ranked,
            String canonicalGapId,
            boolean gapRuleBlocking,
            int askCount,
            String escalationLevel) {
        UnresolvedItemLedger base = ledger != null ? ledger : UnresolvedItemLedger.empty();
        if (!ranked.userInputRequired()) {
            return new UpsertResult(base, Optional.empty());
        }
        String q = ranked.questionText() != null ? ranked.questionText().trim() : "";
        if (q.isBlank() || canonicalGapId == null || canonicalGapId.isBlank()) {
            return new UpsertResult(base, Optional.empty());
        }
        if (mergedPlanningCoversSemanticallySimilarQuestion(base, q)) {
            return new UpsertResult(base, Optional.empty());
        }
        if (base.hasFingerprintMergeClosed(q)) {
            return new UpsertResult(base, Optional.empty());
        }
        String fp = UnresolvedItemLedger.normalizeFingerprint(q);
        String inputKind = ranked.useStructuredChoices() ? "bounded_choice" : "open";
        Map<String, String> source = new LinkedHashMap<>();
        source.put("channel", PLANNING_CLARIFICATION_CHANNEL);
        source.put("gapId", canonicalGapId.trim());
        source.put("topicKey", canonicalGapId.trim());
        source.put("inputKind", inputKind);
        source.put("lastAskedAt", String.valueOf(System.currentTimeMillis()));
        if (askCount > 0) {
            source.put("askCount", String.valueOf(askCount));
        }
        if (escalationLevel != null && !escalationLevel.isBlank()) {
            source.put("escalationLevel", escalationLevel.trim());
        }
        boolean blocking = gapRuleBlocking || ranked.blockingQuestionCount() > 0;
        String severity = blocking ? "blocking" : "normal";

        Optional<UnresolvedItem> active = base.findOpenPlanningByGapId(canonicalGapId);
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
                        List.of(),
                        List.of(),
                        0);
        return new UpsertResult(withoutOthers.withAdded(created), Optional.of(id));
    }

    public static int planningGapAskCount(UnresolvedItemLedger ledger, String gapId) {
        if (ledger == null || gapId == null || gapId.isBlank()) {
            return 0;
        }
        int count = 0;
        String normalized = gapId.trim();
        for (UnresolvedItem it : ledger.items()) {
            if (!PLANNING_CLARIFICATION_CHANNEL.equals(it.getSource().get("channel"))) {
                continue;
            }
            if (!normalized.equals(it.getSource().getOrDefault("gapId", "").trim())) {
                continue;
            }
            count++;
        }
        return count;
    }

    /**
     * Cancel OPEN {@code planning_clarification} items that are not in {@code allowedOpenGapIds}. When the set is empty,
     * cancels every OPEN planning clarification (no canonical gaps remain).
     */
    public static UnresolvedItemLedger reconcileCanonicalOpenGaps(
            UnresolvedItemLedger ledger, Set<String> allowedOpenGapIds) {
        UnresolvedItemLedger base = ledger != null ? ledger : UnresolvedItemLedger.empty();
        Set<String> allowed = allowedOpenGapIds != null ? allowedOpenGapIds : Set.of();
        List<UnresolvedItem> next = new ArrayList<>();
        for (UnresolvedItem it : base.items()) {
            if (it.getStatus() == UnresolvedItemStatus.OPEN
                    && PLANNING_CLARIFICATION_CHANNEL.equals(it.getSource().get("channel"))) {
                String gid = it.getSource().getOrDefault("gapId", "");
                boolean cancel = allowed.isEmpty() || gid.isBlank() || !allowed.contains(gid.trim());
                if (cancel) {
                    next.add(
                            it.withStatus(UnresolvedItemStatus.CANCELLED)
                                    .withMergedSource(
                                            Map.of(
                                                    "resolutionNotes",
                                                    allowed.isEmpty()
                                                            ? "Canonical gap evaluation: no open coordinator gaps."
                                                            : "Canonical gap evaluation: gap no longer active or superseded.")));
                    continue;
                }
            }
            next.add(it);
        }
        return new UnresolvedItemLedger(next);
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
        return mergeAnswerIntoLedger(ledger, ledgerItemId, questionFingerprint, null, rawAnswer, normalizedAnswer);
    }

    /**
     * Same as {@link #mergeAnswerIntoLedger(UnresolvedItemLedger, String, String, String, String)} with optional
     * {@code coordinatorGapId} to locate the OPEN item when the ledger item id was cleared from session.
     */
    public static UnresolvedItemLedger mergeAnswerIntoLedger(
            UnresolvedItemLedger ledger,
            String ledgerItemId,
            String questionFingerprint,
            String coordinatorGapId,
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
        if (coordinatorGapId != null && !coordinatorGapId.isBlank()) {
            Optional<UnresolvedItem> byGap = base.findOpenPlanningByGapId(coordinatorGapId);
            if (byGap.isPresent()) {
                return replaceMerged(base, byGap.get(), rawAnswer, normalizedAnswer);
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

    /**
     * If a MERGED planning-clarification item exists whose text is highly similar to {@code questionText}, do not open a
     * new question (paraphrase after successful merge).
     */
    private static boolean mergedPlanningCoversSemanticallySimilarQuestion(
            UnresolvedItemLedger ledger, String questionText) {
        if (ledger == null || questionText == null || questionText.isBlank()) {
            return false;
        }
        for (UnresolvedItem it : ledger.items()) {
            if (it.getStatus() != UnresolvedItemStatus.MERGED) {
                continue;
            }
            if (!PLANNING_CLARIFICATION_CHANNEL.equals(it.getSource().get("channel"))) {
                continue;
            }
            String prev = it.getQuestionText();
            if (prev == null || prev.isBlank()) {
                continue;
            }
            if (ClarificationTextSimilarity.clarificationSimilarity(prev, questionText) >= 0.82) {
                return true;
            }
        }
        return false;
    }

    /**
     * Latest merged planning clarification question text for a coordinator {@code gapId} (stable id in item source), or empty
     * when none. Used to avoid repeating the same template after a user merge.
     */
    public static Optional<String> lastMergedQuestionTextForPlanningGap(UnresolvedItemLedger ledger, String gapId) {
        if (ledger == null || gapId == null || gapId.isBlank()) {
            return Optional.empty();
        }
        String gid = gapId.trim();
        String last = null;
        for (UnresolvedItem it : ledger.items()) {
            if (it.getStatus() != UnresolvedItemStatus.MERGED) {
                continue;
            }
            if (!PLANNING_CLARIFICATION_CHANNEL.equals(it.getSource().get("channel"))) {
                continue;
            }
            if (!gid.equals(it.getSource().getOrDefault("gapId", "").trim())) {
                continue;
            }
            String q = it.getQuestionText();
            if (q != null && !q.isBlank()) {
                last = q.trim();
            }
        }
        return Optional.ofNullable(last);
    }

    /** Escalation label persisted on ledger rows ({@code OPEN} / {@code NARROW} / {@code BOUNDED}). */
    public static String escalationLevelForPriorAskCount(int priorAsksBeforeThisCycle) {
        if (priorAsksBeforeThisCycle >= 2) {
            return "BOUNDED";
        }
        if (priorAsksBeforeThisCycle >= 1) {
            return "NARROW";
        }
        return "OPEN";
    }

    public static String firstOpenPlanningClarificationQuestionText(UnresolvedItemLedger ledger) {
        if (ledger == null) {
            return "";
        }
        for (UnresolvedItem it : ledger.items()) {
            if (it.getStatus() != UnresolvedItemStatus.OPEN) {
                continue;
            }
            if (!PLANNING_CLARIFICATION_CHANNEL.equals(it.getSource().get("channel"))) {
                continue;
            }
            String q = it.getQuestionText();
            if (q != null && !q.isBlank()) {
                return q.trim();
            }
        }
        return "";
    }

    public static FeaturePlanState applyCanonicalGapAskSurfaced(
            FeaturePlanState plan,
            FeaturePlanStateStore store,
            String contextId,
            CanonicalPlanningGap gap,
            int cycleIteration) {
        if (plan == null || store == null || gap == null || contextId == null || contextId.isBlank()) {
            return plan;
        }
        FeaturePlanState next =
                plan.withClarificationQuestionSurfaced("ask:" + gap.gapId() + ":" + cycleIteration)
                        .withPlanningGapAskCountsJson(
                                PlanningGapAskCounts.incrementAsk(plan.getPlanningGapAskCountsJson(), gap.gapId()))
                        .withPlanningIntakeStage(PlanningIntakeStage.CLARIFYING, java.time.Instant.now());
        store.update(next);
        return store.getByContextId(contextId).orElse(next);
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
