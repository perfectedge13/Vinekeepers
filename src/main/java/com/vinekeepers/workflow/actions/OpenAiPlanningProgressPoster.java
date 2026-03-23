package com.vinekeepers.workflow.actions;

import com.vinekeepers.connectors.openai.OpenAiPlanningProgressSink;
import com.vinekeepers.env.Env;
import com.vinekeepers.events.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;

/**
 * Posts planning OpenAI activity to the workflow channel using {@link PostChannelMessageAction} (same targeting rules
 * as other lifecycle posts). Disabled when {@code OPENAI_PLANNING_DISCORD_PROGRESS=false}.
 */
public final class OpenAiPlanningProgressPoster implements OpenAiPlanningProgressSink {

    private static final Logger log = LoggerFactory.getLogger(OpenAiPlanningProgressPoster.class);

    private final PostChannelMessageAction postChannelMessageAction;

    public OpenAiPlanningProgressPoster(PostChannelMessageAction postChannelMessageAction) {
        this.postChannelMessageAction = Objects.requireNonNull(postChannelMessageAction, "postChannelMessageAction");
    }

    @Override
    public void publish(Event event, Map<String, Object> workflowState, String userFacingLine) {
        if (!discordProgressEnabled()) {
            return;
        }
        if (event == null || workflowState == null || userFacingLine == null || userFacingLine.isBlank()) {
            return;
        }
        String content = "**Update:** " + userFacingLine.trim();
        try {
            Object r = postChannelMessageAction.run(event, workflowState, Map.of("content", content));
            if (!"OK".equals(String.valueOf(r))) {
                log.debug("OpenAI planning progress post result: {}", r);
            }
        } catch (Exception e) {
            log.debug("OpenAI planning progress post failed: {}", e.getMessage());
        }
    }

    private static boolean discordProgressEnabled() {
        String v = Env.get("OPENAI_PLANNING_DISCORD_PROGRESS", "true");
        return v == null || !"false".equalsIgnoreCase(v.trim());
    }
}
