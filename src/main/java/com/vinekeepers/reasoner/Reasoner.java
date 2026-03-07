package com.vinekeepers.reasoner;

/**
 * Reasoner interface (LLM / decision step).
 */
@FunctionalInterface
public interface Reasoner {

    ReasonerOutput reason(ReasonerInput input);
}
