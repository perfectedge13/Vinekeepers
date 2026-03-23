package com.vinekeepers.workflow.planning;

import com.vinekeepers.connectors.openai.OpenAiChatClient;
import com.vinekeepers.events.Event;
import com.vinekeepers.profile.WorkProfileDefinition;
import com.vinekeepers.profile.WorkProfileRegistry;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.FeaturePlanStateStore;

import java.util.List;
import java.util.Map;

/**
 * Runs one OpenAI-backed Arrietty planning pass.
 * Delegates execution to {@link StructuredLlmArtifactUpsertPass}.
 */
public final class PlanningRolePassRunner {

    private PlanningRolePassRunner() {}

    public record RolePassResult(int upsertsApplied, List<String> followUps, String error, boolean skipped) {}

    public static RolePassResult run(
            PlanningCoordinatorRole role,
            OpenAiChatClient client,
            FeaturePlanState plan,
            WorkProfileDefinition profile,
            Event event,
            Map<String, Object> state,
            FeaturePlanStateStore planStore,
            WorkProfileRegistry profileRegistry) {
        return StructuredLlmArtifactUpsertPass.execute(
                client,
                plan,
                profile,
                event,
                state,
                planStore,
                profileRegistry,
                role.name(),
                role.systemPromptBlock(),
                role.userTaskHint());
    }
}
