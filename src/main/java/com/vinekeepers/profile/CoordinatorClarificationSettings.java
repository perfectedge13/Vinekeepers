package com.vinekeepers.profile;

import java.util.List;
import java.util.Objects;

/** Optional {@code coordinatorClarification} block from work profile YAML. */
public final class CoordinatorClarificationSettings {

    private final CoordinatorClarificationMode mode;
    private final List<CoordinatorClarificationGapRule> gaps;

    public CoordinatorClarificationSettings(CoordinatorClarificationMode mode, List<CoordinatorClarificationGapRule> gaps) {
        this.mode = mode != null ? mode : CoordinatorClarificationMode.LEGACY;
        this.gaps = gaps != null ? List.copyOf(gaps) : List.of();
    }

    public CoordinatorClarificationMode getMode() {
        return mode;
    }

    public List<CoordinatorClarificationGapRule> getGaps() {
        return gaps;
    }

    /** Default when YAML omits the block: preserve legacy coordinator clarification. */
    public static CoordinatorClarificationSettings legacyDefault() {
        return new CoordinatorClarificationSettings(CoordinatorClarificationMode.LEGACY, List.of());
    }

    public boolean isCanonicalV1() {
        return mode == CoordinatorClarificationMode.CANONICAL_V1;
    }
}
