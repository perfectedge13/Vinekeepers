package com.vinekeepers.tools;

import java.util.Map;

/**
 * Simple echo tool for configured workflow examples.
 */
public final class EchoTool implements Tool {

    public static final String ID = "echo";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public Object run(Map<String, Object> args) {
        return args != null ? args.get("message") : null;
    }
}
