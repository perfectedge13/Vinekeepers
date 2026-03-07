package com.vinekeepers.bot;

import java.util.Objects;

/**
 * Persona configuration for a bot (name, system prompt, etc.).
 */
public final class Persona {

    private final String name;
    private final String systemPrompt;

    public Persona(String name, String systemPrompt) {
        this.name = Objects.requireNonNull(name, "name");
        this.systemPrompt = systemPrompt != null ? systemPrompt : "";
    }

    public String getName() {
        return name;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }
}
