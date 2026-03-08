package com.vinekeepers.reasoner;

import java.util.Map;

/**
 * Tool call proposed by a reasoner. Engine validates and executes it through ToolRunner.
 */
public record ProposedToolCall(String toolId, Map<String, Object> args) {

    public ProposedToolCall {
        args = args != null ? Map.copyOf(args) : Map.of();
    }
}
