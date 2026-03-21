package com.vinekeepers.providers;

import com.vinekeepers.events.Event;
import com.vinekeepers.gadget.GadgetProjectDefinition;
import com.vinekeepers.gadget.GadgetProjectRegistry;
import com.vinekeepers.workflow.ConfigurableWorkflowState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GitRemoteBranchesChoiceProviderTest {

    @Test
    void fallsBackWhenGitRemoteMissing() {
        GadgetProjectRegistry reg = new GadgetProjectRegistry(List.of(
                new GadgetProjectDefinition("p1", "P", "playbooks/x.yml")));
        GitRemoteBranchesChoiceProvider p = new GitRemoteBranchesChoiceProvider(reg);
        ConfigurableWorkflowState st = new ConfigurableWorkflowState();
        st.put("gadgetProject", "p1");
        List<com.vinekeepers.interactions.ResponseIntent.Choice> choices =
                p.getChoices(new Event("s", "message", Map.of()), st);
        assertTrue(choices.stream().anyMatch(c -> "main".equals(c.id())));
        assertTrue(choices.stream().anyMatch(c -> "other".equals(c.id())));
    }
}
