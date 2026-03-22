package com.vinekeepers.providers;

import com.vinekeepers.devops.DeployTarget;
import com.vinekeepers.devops.DeployTargetCompose;
import com.vinekeepers.devops.DeployTargetRegistry;
import com.vinekeepers.events.Event;
import com.vinekeepers.workflow.ConfigurableWorkflowState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;

class GitRemoteBranchesChoiceProviderTest {

    @Test
    void fallsBackWhenGitRemoteMissing() {
        DeployTargetRegistry reg = new DeployTargetRegistry(List.of(
                new DeployTarget("p1", "P", "playbooks/x.yml", null, Map.of(), DeployTargetCompose.NONE)));
        GitRemoteBranchesChoiceProvider p = new GitRemoteBranchesChoiceProvider(reg);
        ConfigurableWorkflowState st = new ConfigurableWorkflowState();
        st.put("deployTargetId", "p1");
        assertFalse(p.getChoices(new Event("s", "k", Map.of()), st).isEmpty());
    }
}
