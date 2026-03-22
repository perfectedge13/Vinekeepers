package com.vinekeepers.state.workflow;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * In-memory ledger of unresolved items with JSON round-trip for workflow state keys
 * (e.g. {@code workflowUnresolvedItemsJson}).
 */
public final class UnresolvedItemLedger {

    public static final String STATE_JSON_KEY = "workflowUnresolvedItemsJson";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final List<UnresolvedItem> items;

    public UnresolvedItemLedger(List<UnresolvedItem> items) {
        this.items = items != null ? new ArrayList<>(items) : new ArrayList<>();
    }

    public static UnresolvedItemLedger empty() {
        return new UnresolvedItemLedger(List.of());
    }

    public static UnresolvedItemLedger fromStateJson(String json) {
        if (json == null || json.isBlank()) {
            return empty();
        }
        try {
            List<Map<String, Object>> raw = JSON.readValue(json, new TypeReference<>() {});
            List<UnresolvedItem> out = new ArrayList<>();
            for (Map<String, Object> m : raw) {
                UnresolvedItem it = UnresolvedItem.fromMap(m);
                if (it != null) {
                    out.add(it);
                }
            }
            return new UnresolvedItemLedger(out);
        } catch (Exception e) {
            return empty();
        }
    }

    public String toStateJson() {
        try {
            List<Map<String, Object>> raw = new ArrayList<>();
            for (UnresolvedItem it : items) {
                raw.add(it.toMap());
            }
            return JSON.writeValueAsString(raw);
        } catch (Exception e) {
            return "[]";
        }
    }

    public List<UnresolvedItem> items() {
        return List.copyOf(items);
    }

    public boolean hasOpenItems() {
        for (UnresolvedItem it : items) {
            if (it.getStatus() == UnresolvedItemStatus.OPEN
                    || it.getStatus() == UnresolvedItemStatus.ANSWERED
                    || it.getStatus() == UnresolvedItemStatus.BLOCKED) {
                return true;
            }
        }
        return false;
    }

    /**
     * True if this fingerprint has been closed in a way that must not be re-asked until explicitly invalidated.
     * {@link UnresolvedItemStatus#INVALIDATED} does not block re-surfacing the same fingerprint.
     */
    public boolean hasFingerprintMergeClosed(String fingerprint) {
        if (fingerprint == null || fingerprint.isBlank()) {
            return false;
        }
        String norm = normalizeFingerprint(fingerprint);
        for (UnresolvedItem it : items) {
            if (!norm.equals(it.getFingerprint())) {
                continue;
            }
            UnresolvedItemStatus s = it.getStatus();
            if (s == UnresolvedItemStatus.MERGED
                    || s == UnresolvedItemStatus.RESOLVED_ASSUMPTION
                    || s == UnresolvedItemStatus.CANCELLED) {
                return true;
            }
        }
        return false;
    }

    /** First item in OPEN, ANSWERED, or BLOCKED with this normalized fingerprint, if any. */
    public Optional<UnresolvedItem> findActiveByFingerprint(String fingerprint) {
        if (fingerprint == null || fingerprint.isBlank()) {
            return Optional.empty();
        }
        String norm = normalizeFingerprint(fingerprint);
        for (UnresolvedItem it : items) {
            if (!norm.equals(it.getFingerprint())) {
                continue;
            }
            if (it.getStatus() == UnresolvedItemStatus.OPEN
                    || it.getStatus() == UnresolvedItemStatus.ANSWERED
                    || it.getStatus() == UnresolvedItemStatus.BLOCKED) {
                return Optional.of(it);
            }
        }
        return Optional.empty();
    }

    /**
     * Cancel other planning-clarification OPEN items so at most one active question is tracked.
     */
    public UnresolvedItemLedger withCancelledOpenPlanningExcept(String keepId) {
        if (keepId == null) {
            keepId = "";
        }
        List<UnresolvedItem> next = new ArrayList<>();
        for (UnresolvedItem it : items) {
            if (shouldCancelPlanningOpen(it, keepId)) {
                next.add(
                        it.withStatus(UnresolvedItemStatus.CANCELLED)
                                .withMergedSource(Map.of(
                                        "resolutionNotes",
                                        "Superseded by a newer clarification request.")));
            } else {
                next.add(it);
            }
        }
        return new UnresolvedItemLedger(next);
    }

    private static boolean shouldCancelPlanningOpen(UnresolvedItem it, String keepId) {
        if (it.getStatus() != UnresolvedItemStatus.OPEN) {
            return false;
        }
        if (keepId.equals(it.getId())) {
            return false;
        }
        return "planning_clarification".equals(it.getSource().get("channel"));
    }

    public Optional<UnresolvedItem> findByFingerprint(String fingerprint) {
        if (fingerprint == null || fingerprint.isBlank()) {
            return Optional.empty();
        }
        String norm = normalizeFingerprint(fingerprint);
        for (UnresolvedItem it : items) {
            if (norm.equals(it.getFingerprint())) {
                return Optional.of(it);
            }
        }
        return Optional.empty();
    }

    /** Maximum {@link UnresolvedItem#getRepeatCount()} among {@link UnresolvedItemStatus#OPEN} items. */
    public int maxOpenItemRepeatCount() {
        int max = 0;
        for (UnresolvedItem it : items) {
            if (it.getStatus() == UnresolvedItemStatus.OPEN && it.getRepeatCount() > max) {
                max = it.getRepeatCount();
            }
        }
        return max;
    }

    public int countOpenMatchingFingerprint(String fingerprint) {
        if (fingerprint == null || fingerprint.isBlank()) {
            return 0;
        }
        String norm = normalizeFingerprint(fingerprint);
        int n = 0;
        for (UnresolvedItem it : items) {
            if (norm.equals(it.getFingerprint())
                    && (it.getStatus() == UnresolvedItemStatus.OPEN
                            || it.getStatus() == UnresolvedItemStatus.BLOCKED)) {
                n++;
            }
        }
        return n;
    }

    public UnresolvedItemLedger withAdded(UnresolvedItem item) {
        List<UnresolvedItem> next = new ArrayList<>(items);
        next.add(item);
        return new UnresolvedItemLedger(next);
    }

    public UnresolvedItemLedger withReplaced(String id, UnresolvedItem replacement) {
        List<UnresolvedItem> next = new ArrayList<>();
        for (UnresolvedItem it : items) {
            if (it.getId().equals(id)) {
                next.add(replacement);
            } else {
                next.add(it);
            }
        }
        return new UnresolvedItemLedger(next);
    }

    public static String normalizeFingerprint(String text) {
        if (text == null) {
            return "";
        }
        String t = text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
        return t.replaceAll("[^a-z0-9?\\s]", "");
    }

    public static String newId() {
        return "uq_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    /**
     * Merge into a mutable state map under {@link #STATE_JSON_KEY}.
     */
    public void putInto(Map<String, Object> state) {
        if (state != null) {
            state.put(STATE_JSON_KEY, toStateJson());
        }
    }

    public static UnresolvedItemLedger readFrom(Map<String, Object> state) {
        if (state == null) {
            return empty();
        }
        Object raw = state.get(STATE_JSON_KEY);
        return fromStateJson(raw != null ? raw.toString() : "");
    }

    public static void mergeLedgerIntoSpread(Map<String, Object> spread, UnresolvedItemLedger ledger) {
        if (spread != null && ledger != null) {
            spread.put(STATE_JSON_KEY, ledger.toStateJson());
            spread.put("workflowUnresolvedHasOpen", ledger.hasOpenItems() ? "true" : "false");
        }
    }
}
