package com.vinekeepers.workflow.planning;

import com.vinekeepers.connectors.openai.OpenAiChatClient;

/**
 * Default {@link PlanningContentGenerator} delegating to {@link OpenAiChatClient#complete(String, String, String, Long)}.
 */
public final class OpenAiPlanningContentGenerator implements PlanningContentGenerator {

    public static final OpenAiPlanningContentGenerator INSTANCE = new OpenAiPlanningContentGenerator();

    private OpenAiPlanningContentGenerator() {}

    @Override
    public String complete(
            OpenAiChatClient client,
            String systemPrompt,
            String userPayload,
            String modelOverride,
            Long timeoutMsOverride) {
        if (client == null) {
            return "ERROR: no OpenAI client";
        }
        return client.complete(systemPrompt, userPayload, modelOverride, timeoutMsOverride);
    }
}
