package com.vinekeepers.workflow.actions;

import com.vinekeepers.events.Event;
import com.vinekeepers.gadget.GadgetProjectDefinition;
import com.vinekeepers.gadget.GadgetProjectRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeployResolveProjectActionTest {

    @Test
    void singleProjectSkipsPick() {
        GadgetProjectRegistry reg = new GadgetProjectRegistry(List.of(
                new GadgetProjectDefinition("only", "Only", "p.yml")));
        DeployResolveProjectAction action = new DeployResolveProjectAction(reg);
        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) action.run(new Event("s", "message", Map.of()), Map.of(), Map.of());
        assertEquals("only", out.get("gadgetProject"));
        assertEquals("false", out.get("deployNeedsProjectPick"));
    }

    @Test
    void multipleProjectsNeedsPickWhenNoDefault() {
        GadgetProjectRegistry reg = new GadgetProjectRegistry(List.of(
                new GadgetProjectDefinition("a", "A", "a.yml"),
                new GadgetProjectDefinition("b", "B", "b.yml")));
        DeployResolveProjectAction action = new DeployResolveProjectAction(reg);
        String pd = System.getProperty("DEPLOY_DEFAULT_PROJECT");
        String pg = System.getProperty("GADGET_DEFAULT_PROJECT");
        try {
            System.clearProperty("DEPLOY_DEFAULT_PROJECT");
            System.clearProperty("GADGET_DEFAULT_PROJECT");
            @SuppressWarnings("unchecked")
            Map<String, Object> out = (Map<String, Object>) action.run(new Event("s", "message", Map.of()), Map.of(), Map.of());
            assertEquals("true", out.get("deployNeedsProjectPick"));
        } finally {
            restoreProp("DEPLOY_DEFAULT_PROJECT", pd);
            restoreProp("GADGET_DEFAULT_PROJECT", pg);
        }
    }

    @Test
    void multipleProjectsUsesDeployDefaultProjectWhenSet() {
        GadgetProjectRegistry reg = new GadgetProjectRegistry(List.of(
                new GadgetProjectDefinition("a", "A", "a.yml"),
                new GadgetProjectDefinition("b", "B", "b.yml")));
        DeployResolveProjectAction action = new DeployResolveProjectAction(reg);
        String pd = System.getProperty("DEPLOY_DEFAULT_PROJECT");
        String pg = System.getProperty("GADGET_DEFAULT_PROJECT");
        try {
            System.clearProperty("GADGET_DEFAULT_PROJECT");
            System.setProperty("DEPLOY_DEFAULT_PROJECT", "b");
            @SuppressWarnings("unchecked")
            Map<String, Object> out = (Map<String, Object>) action.run(new Event("s", "message", Map.of()), Map.of(), Map.of());
            assertEquals("b", out.get("gadgetProject"));
            assertEquals("false", out.get("deployNeedsProjectPick"));
        } finally {
            restoreProp("DEPLOY_DEFAULT_PROJECT", pd);
            restoreProp("GADGET_DEFAULT_PROJECT", pg);
        }
    }

    private static void restoreProp(String key, String previous) {
        if (previous == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, previous);
        }
    }
}
