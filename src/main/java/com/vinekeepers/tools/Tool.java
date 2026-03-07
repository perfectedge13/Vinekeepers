package com.vinekeepers.tools;

import java.util.Map;

/**
 * A tool that can be invoked by the reasoner (e.g. search, API call).
 */
public interface Tool {

    String getId();

    /**
     * Execute with the given arguments; returns result as a string or structured data.
     */
    Object run(Map<String, Object> args);
}
