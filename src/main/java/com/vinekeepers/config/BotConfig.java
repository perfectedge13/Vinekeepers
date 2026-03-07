package com.vinekeepers.config;

import java.util.List;
import java.util.Map;

/**
 * YAML-friendly bot and routing config (raw maps/lists from SnakeYAML).
 */
public final class BotConfig {

    private List<Map<String, Object>> bots;
    private List<Map<String, Object>> routing;
    private Map<String, Object> workflows;

    public List<Map<String, Object>> getBots() {
        return bots;
    }

    public void setBots(List<Map<String, Object>> bots) {
        this.bots = bots;
    }

    public List<Map<String, Object>> getRouting() {
        return routing;
    }

    public void setRouting(List<Map<String, Object>> routing) {
        this.routing = routing;
    }

    public Map<String, Object> getWorkflows() {
        return workflows;
    }

    public void setWorkflows(Map<String, Object> workflows) {
        this.workflows = workflows;
    }
}
