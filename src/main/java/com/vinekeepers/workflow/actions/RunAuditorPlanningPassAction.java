package com.vinekeepers.workflow.actions;

import com.vinekeepers.connectors.openai.OpenAiChatClient;
import com.vinekeepers.events.Event;
import com.vinekeepers.profile.WorkProfileRegistry;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.workflow.planning.PlanningCoordinatorRole;

import java.util.Map;

/** Thin wrapper: Auditor / Toad pass. */
public final class RunAuditorPlanningPassAction implements com.vinekeepers.workflow.WorkflowAction {

    private final OpenAiChatClient client;
    private final FeaturePlanStateStore planStore;
    private final WorkProfileRegistry profiles;

    public RunAuditorPlanningPassAction(
            OpenAiChatClient client, FeaturePlanStateStore planStore, WorkProfileRegistry profiles) {
        this.client = client;
        this.planStore = planStore;
        this.profiles = profiles;
    }

    @Override
    public Object run(Event event, Map<String, Object> state, Map<String, Object> bind) {
        return RunArchitectPlanningPassAction.runRole(
                PlanningCoordinatorRole.AUDITOR, client, planStore, profiles, event, state, bind);
    }
}
