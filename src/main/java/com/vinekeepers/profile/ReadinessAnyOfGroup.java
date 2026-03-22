package com.vinekeepers.profile;

import java.util.List;
import java.util.Objects;

/**
 * At least one {@link ReadinessPathRule} in the group must fully pass (config-driven OR bundle).
 */
public final class ReadinessAnyOfGroup {

    private final List<ReadinessPathRule> rules;

    public ReadinessAnyOfGroup(List<ReadinessPathRule> rules) {
        this.rules = rules != null ? List.copyOf(rules) : List.of();
    }

    public List<ReadinessPathRule> getRules() {
        return rules;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ReadinessAnyOfGroup that = (ReadinessAnyOfGroup) o;
        return Objects.equals(rules, that.rules);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rules);
    }
}
