package com.vinekeepers.workflow.actions;

import com.vinekeepers.connectors.openai.OpenAiChatClient;
import com.vinekeepers.events.Event;
import com.vinekeepers.profile.WorkProfileDefinition;
import com.vinekeepers.profile.WorkProfileRegistry;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.workflow.planning.PlanningCoordinatorRole;
import com.vinekeepers.workflow.planning.PlanningRolePassRunner;
import com.vinekeepers.workflow.planning.PlanningRolePassRunner.RolePassResult;

import java.util.LinkedHashMap;
import java.util.Map;

/** Thin wrapper for the consolidated Arrietty planning pass. */
public final class RunArchitectPlanningPassAction implements com.vinekeepers.workflow.WorkflowAction {

    private final OpenAiChatClient client;
    private final FeaturePlanStateStore planStore;
    private final WorkProfileRegistry profiles;

    public RunArchitectPlanningPassAction(
            OpenAiChatClient client, FeaturePlanStateStore planStore, WorkProfileRegistry profiles) {
        this.client = client;
        this.planStore = planStore;
        this.profiles = profiles;
    }

    @Override
    public Object run(Event event, Map<String, Object> state, Map<String, Object> bind) {
        return runRole(PlanningCoordinatorRole.COORDINATOR, client, planStore, profiles, event, state, bind);
    }

    static Map<String, Object> runRole(
            PlanningCoordinatorRole role,
            OpenAiChatClient client,
            FeaturePlanStateStore planStore,
            WorkProfileRegistry profiles,
            Event event,
            Map<String, Object> state,
            Map<String, Object> bind) {
        Map<String, Object> spread = new LinkedHashMap<>();
        spread.put("planningRolePass", role.name());
        spread.put("planningRolePassUpserts", "0");
        spread.put("planningRolePassSkipped", "false");
        spread.put("planningRolePassError", "");
        if (planStore == null || profiles == null) {
            spread.put("planningRolePassError", "MISSING_DEPS");
            return spread;
        }
        String contextId = firstNonBlank(getString(bind, "contextId"), getString(state, "contextId"));
        if (contextId == null || contextId.isBlank()) {
            spread.put("planningRolePassError", "NO_CONTEXT");
            return spread;
        }
        FeaturePlanState plan = planStore.getByContextId(contextId).orElse(null);
        if (plan == null) {
            spread.put("planningRolePassError", "NO_PLAN");
            return spread;
        }
        WorkProfileDefinition profile =
                plan.getProfileId() != null ? profiles.get(plan.getProfileId()).orElse(null) : null;
        if (profile == null) {
            spread.put("planningRolePassError", "NO_PROFILE");
            return spread;
        }
        RolePassResult r = PlanningRolePassRunner.run(role, client, plan, profile, event, state, planStore, profiles);
        spread.put("planningRolePassUpserts", String.valueOf(r.upsertsApplied()));
        spread.put("planningRolePassSkipped", r.skipped() ? "true" : "false");
        spread.put("planningRolePassError", r.error() != null ? r.error() : "");
        return spread;
    }

    private static String getString(Map<String, Object> map, String key) {
        if (map == null) {
            return null;
        }
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : (b != null && !b.isBlank() ? b : null);
    }
}
