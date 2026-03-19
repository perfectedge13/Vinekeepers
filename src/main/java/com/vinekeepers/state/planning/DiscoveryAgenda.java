package com.vinekeepers.state.planning;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

/**
 * Ordered discovery questions and summary fields for workflow state / JSON snapshots.
 */
public final class DiscoveryAgenda {

    private final List<DiscoveryQuestion> questions;
    private final int openGapCount;
    private final String nextQuestionId;
    private final String generatedAt;

    @JsonCreator
    public DiscoveryAgenda(
            @JsonProperty("questions") List<DiscoveryQuestion> questions,
            @JsonProperty("openGapCount") int openGapCount,
            @JsonProperty("nextQuestionId") String nextQuestionId,
            @JsonProperty("generatedAt") String generatedAt) {
        this.questions = questions != null ? List.copyOf(questions) : List.of();
        this.openGapCount = openGapCount;
        this.nextQuestionId = nextQuestionId != null ? nextQuestionId : "";
        this.generatedAt = generatedAt != null ? generatedAt : "";
    }

    public List<DiscoveryQuestion> getQuestions() {
        return questions;
    }

    public int getOpenGapCount() {
        return openGapCount;
    }

    public String getNextQuestionId() {
        return nextQuestionId;
    }

    public String getGeneratedAt() {
        return generatedAt;
    }

    public DiscoveryQuestion firstQuestion() {
        return questions.isEmpty() ? null : questions.get(0);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DiscoveryAgenda that = (DiscoveryAgenda) o;
        return openGapCount == that.openGapCount
                && Objects.equals(questions, that.questions)
                && Objects.equals(nextQuestionId, that.nextQuestionId)
                && Objects.equals(generatedAt, that.generatedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(questions, openGapCount, nextQuestionId, generatedAt);
    }
}
