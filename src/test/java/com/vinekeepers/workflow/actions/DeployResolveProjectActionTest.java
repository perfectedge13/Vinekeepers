package com.vinekeepers.workflow.actions;

import com.vinekeepers.devops.DeployTarget;
import com.vinekeepers.devops.DeployTargetCompose;
import com.vinekeepers.devops.DeployTargetRegistry;
import com.vinekeepers.events.Event;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeployResolveProjectActionTest {

    @Test
    void singleTargetSkipsPick() {
        DeployTargetRegistry reg = new DeployTargetRegistry(List.of(
                new DeployTarget("only", "Only", "p.yml", null, Map.of(), DeployTargetCompose.NONE)));
        DeployResolveProjectAction action = new DeployResolveProjectAction(reg);
        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) action.run(new Event("s", "message", Map.of()), Map.of(), Map.of());
        assertEquals("only", out.get("deployTargetId"));
        assertEquals("false", out.get("deployNeedsProjectPick"));
    }

    @Test
    void multipleTargetsNeedsPickWhenNoDefault() {
        DeployTargetRegistry reg = new DeployTargetRegistry(List.of(
                new DeployTarget("a", "A", "a.yml", null, Map.of(), DeployTargetCompose.NONE),
                new DeployTarget("b", "B", "b.yml", null, Map.of(), DeployTargetCompose.NONE)));
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
    void multipleTargetsUsesDeployDefaultProjectWhenSet() {
        DeployTargetRegistry reg = new DeployTargetRegistry(List.of(
                new DeployTarget("a", "A", "a.yml", null, Map.of(), DeployTargetCompose.NONE),
                new DeployTarget("b", "B", "b.yml", null, Map.of(), DeployTargetCompose.NONE)));
        DeployResolveProjectAction action = new DeployResolveProjectAction(reg);
        String pd = System.getProperty("DEPLOY_DEFAULT_PROJECT");
        String pg = System.getProperty("GADGET_DEFAULT_PROJECT");
        try {
            System.clearProperty("GADGET_DEFAULT_PROJECT");
            System.setProperty("DEPLOY_DEFAULT_PROJECT", "b");
            @SuppressWarnings("unchecked")
            Map<String, Object> out = (Map<String, Object>) action.run(new Event("s", "message", Map.of()), Map.of(), Map.of());
            assertEquals("b", out.get("deployTargetId"));
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
