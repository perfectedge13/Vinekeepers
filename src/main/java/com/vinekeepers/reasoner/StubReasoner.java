package com.vinekeepers.reasoner;

/**
 * Stub reasoner that returns a fixed response.
 */
public final class StubReasoner implements Reasoner {

    @Override
    public ReasonerOutput reason(ReasonerInput input) {
        return ReasonerOutput.empty();
    }
}
