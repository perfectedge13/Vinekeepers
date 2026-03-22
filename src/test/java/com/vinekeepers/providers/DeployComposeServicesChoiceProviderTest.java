package com.vinekeepers.providers;

import com.vinekeepers.devops.ComposeHostExecutor;
import com.vinekeepers.devops.DeployTarget;
import com.vinekeepers.devops.DeployTargetCompose;
import com.vinekeepers.devops.DeployTargetRegistry;
import com.vinekeepers.events.Event;
import com.vinekeepers.workflow.ConfigurableWorkflowState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DeployComposeServicesChoiceProviderTest {

    @Test
    void includesAllAndServiceNames() {
        DeployTargetCompose c = new DeployTargetCompose("f.yml", "/w", List.of("web"), ComposeHostExecutor.DIRECT);
        DeployTargetRegistry reg = new DeployTargetRegistry(List.of(
                new DeployTarget("t1", "T1", "p.yml", null, Map.of(), c)));
        DeployComposeServicesChoiceProvider p = new DeployComposeServicesChoiceProvider(reg);
        ConfigurableWorkflowState st = new ConfigurableWorkflowState();
        st.put("deployTargetId", "t1");
        var choices = p.getChoices(new Event("s", "k", Map.of()), st);
        assertTrue(choices.stream().anyMatch(ch -> "_all".equals(ch.id())));
        assertTrue(choices.stream().anyMatch(ch -> "web".equals(ch.id())));
    }
}
