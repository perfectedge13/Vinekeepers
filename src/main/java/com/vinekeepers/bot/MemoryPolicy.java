package com.vinekeepers.bot;

import java.util.Objects;

/**
 * Policy for context/memory (e.g. max tokens, retention).
 */
public final class MemoryPolicy {

    private final int maxContextTokens;

    public MemoryPolicy(int maxContextTokens) {
        this.maxContextTokens = maxContextTokens > 0 ? maxContextTokens : 4096;
    }

    public int getMaxContextTokens() {
        return maxContextTokens;
    }
}
