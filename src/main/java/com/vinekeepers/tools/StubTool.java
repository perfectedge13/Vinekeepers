package com.vinekeepers.tools;

import java.util.Map;

/**
 * Stub tool for testing/wiring.
 */
public final class StubTool implements Tool {

    public static final String ID = "stub";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public Object run(Map<String, Object> args) {
        return "stub result";
    }
}
