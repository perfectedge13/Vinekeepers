package com.vinekeepers.workflow.actions;

import com.vinekeepers.connectors.OutboundDeliveryRouter;
import com.vinekeepers.events.Event;
import com.vinekeepers.gadget.GadgetProjectDefinition;
import com.vinekeepers.gadget.GadgetProjectRegistry;
import com.vinekeepers.state.LifecycleContextStore;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StartGadgetDeployActionTest {

    @Test
    void returnsFailureWhenProjectUnknown() {
        OutboundDeliveryRouter router = new OutboundDeliveryRouter(new LifecycleContextStore());
        StartGadgetDeployAction action = new StartGadgetDeployAction(router, new GadgetProjectRegistry(List.of()), "gadget");
        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) action.run(
                new Event("discord:g:1", "message", Map.of()),
                Map.of("gadgetProject", "missing", "channelId", "ch1", "deliveryChannelId", "th1", "deployBranch", "main"),
                Map.of());
        assertTrue(out.get("gadgetDeployMessage").toString().contains("Unknown project"));
    }

    @Test
    void dryRunPostsViaSendAsGadget() throws Exception {
        System.setProperty("GADGET_ANSIBLE_DISABLED", "true");
        try {
            List<String> sent = new ArrayList<>();
            OutboundDeliveryRouter router = new OutboundDeliveryRouter(new LifecycleContextStore());
            router.registerSender("gadget", (channelId, messageId, content) -> {
                sent.add(channelId + "::" + content);
            }, null);

            GadgetProjectRegistry reg = new GadgetProjectRegistry(List.of(
                    new GadgetProjectDefinition("vinekeepers", "Vinekeepers", "playbooks/site.yml")));
            StartGadgetDeployAction action = new StartGadgetDeployAction(router, reg, "gadget");
            @SuppressWarnings("unchecked")
            Map<String, Object> out = (Map<String, Object>) action.run(
                    new Event("discord:g:1", "message", Map.of()),
                    Map.of("gadgetProject", "vinekeepers", "channelId", "ch1", "deliveryChannelId", "thread-1",
                            "deployBranch", "main"),
                    Map.of());
            assertTrue(out.get("gadgetDeployMessage").toString().contains("Deploy started"));

            // Background thread posts dry-run line
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (System.nanoTime() < deadline && sent.stream().noneMatch(s -> s.contains("Dry run"))) {
                Thread.sleep(50);
            }
            assertTrue(sent.stream().anyMatch(s -> s.contains("Dry run")),
                    "expected dry-run message, got: " + sent);
        } finally {
            System.clearProperty("GADGET_ANSIBLE_DISABLED");
        }
    }
}
