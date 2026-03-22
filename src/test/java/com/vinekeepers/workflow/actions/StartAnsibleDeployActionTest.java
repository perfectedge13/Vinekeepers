package com.vinekeepers.workflow.actions;

import com.vinekeepers.connectors.OutboundDeliveryRouter;
import com.vinekeepers.devops.DeployTarget;
import com.vinekeepers.devops.DeployTargetCompose;
import com.vinekeepers.devops.DeployTargetRegistry;
import com.vinekeepers.events.Event;
import com.vinekeepers.state.LifecycleContextStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

class StartAnsibleDeployActionTest {

    @Test
    void unknownTargetReturnsMessage() {
        OutboundDeliveryRouter router = new OutboundDeliveryRouter(new LifecycleContextStore());
        StartAnsibleDeployAction action = new StartAnsibleDeployAction(router, new DeployTargetRegistry(List.of()));
        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) action.run(
                new Event("s", "message", Map.of()),
                Map.of("deployTargetId", "missing", "channelId", "ch1", "deliveryChannelId", "th1",
                        "deployBranch", "main", "__botId", "opsbot"),
                Map.of());
        assertTrue(out.get("deployAnsibleMessage").toString().contains("Unknown"));
    }

    @Test
    void missingBotIdReturnsMessage() {
        OutboundDeliveryRouter router = new OutboundDeliveryRouter(new LifecycleContextStore());
        DeployTargetRegistry reg = new DeployTargetRegistry(List.of(
                new DeployTarget("vinekeepers", "Vinekeepers", "playbooks/site.yml", null, Map.of(), DeployTargetCompose.NONE)));
        StartAnsibleDeployAction action = new StartAnsibleDeployAction(router, reg);
        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) action.run(
                new Event("s", "message", Map.of()),
                Map.of("deployTargetId", "vinekeepers", "channelId", "ch1", "deliveryChannelId", "thread-1",
                        "deployBranch", "main"),
                Map.of());
        assertTrue(out.get("deployAnsibleMessage").toString().contains("__botId"));
    }

    @Test
    void dryRunPostsViaSendAsWorkflowBot() throws Exception {
        String prev = System.getProperty("GADGET_ANSIBLE_DISABLED");
        try {
            System.setProperty("GADGET_ANSIBLE_DISABLED", "true");
            OutboundDeliveryRouter router = new OutboundDeliveryRouter(new LifecycleContextStore());
            AtomicInteger calls = new AtomicInteger();
            router.registerSender("opsbot", (channelId, messageId, content) -> {
                calls.incrementAndGet();
            }, null);
            DeployTargetRegistry reg = new DeployTargetRegistry(List.of(
                    new DeployTarget("vinekeepers", "Vinekeepers", "playbooks/site.yml", null, Map.of(), DeployTargetCompose.NONE)));
            StartAnsibleDeployAction action = new StartAnsibleDeployAction(router, reg);
            @SuppressWarnings("unchecked")
            Map<String, Object> out = (Map<String, Object>) action.run(
                    new Event("s", "message", Map.of()),
                    Map.of("deployTargetId", "vinekeepers", "channelId", "ch1", "deliveryChannelId", "thread-1",
                            "deployBranch", "main", "__botId", "opsbot"),
                    Map.of());
            assertTrue(out.get("deployAnsibleMessage").toString().contains("Ansible deploy started"));
            Thread.sleep(800);
            assertTrue(calls.get() >= 1);
        } finally {
            if (prev == null) {
                System.clearProperty("GADGET_ANSIBLE_DISABLED");
            } else {
                System.setProperty("GADGET_ANSIBLE_DISABLED", prev);
            }
        }
    }
}
