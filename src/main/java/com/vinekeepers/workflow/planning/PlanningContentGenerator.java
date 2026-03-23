package com.vinekeepers.workflow.planning;

import com.vinekeepers.connectors.openai.OpenAiCallContext;
import com.vinekeepers.connectors.openai.OpenAiChatClient;

/**
 * OpenAI-backed planning text generation; phases use distinct system prompts and JSON shapes.
 */
public interface PlanningContentGenerator {

    /**
     * @param modelOverride   optional model (from workflow / step {@code llm})
     * @param timeoutMsOverride optional timeout override
     * @param callContext optional Discord/logging context for this exchange
     * @return raw assistant text (may be {@code ERROR:} prefixed)
     */
    String complete(
            OpenAiChatClient client,
            String systemPrompt,
            String userPayload,
            String modelOverride,
            Long timeoutMsOverride,
            OpenAiCallContext callContext);
}
