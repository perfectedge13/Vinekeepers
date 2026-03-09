package com.vinekeepers.connectors;

import com.vinekeepers.interactions.AppReplySink;
import com.vinekeepers.interactions.Capabilities;
import com.vinekeepers.interactions.ChannelTarget;
import com.vinekeepers.interactions.InteractionTarget;
import com.vinekeepers.interactions.OutboundResponse;
import com.vinekeepers.interactions.ReplyTarget;
import com.vinekeepers.interactions.CollectForm;
import com.vinekeepers.interactions.CollectText;
import com.vinekeepers.interactions.ConfirmAction;
import com.vinekeepers.interactions.PresentChoices;
import com.vinekeepers.interactions.ResponseIntent;
import com.vinekeepers.interactions.ResponseIntentType;
import com.vinekeepers.interactions.ShowActions;
import com.vinekeepers.interactions.ShowStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Discord implementation of the connector reply contract. Lifecycle operations;
 * defer is adapter-internal only (handled in gateway on interaction receive).
 * Falls back to text when intent is not supported.
 */
public final class DiscordAppReplySink implements AppReplySink {

    private static final Logger log = LoggerFactory.getLogger(DiscordAppReplySink.class);
    private static final int DISCORD_DEFER_MS = 2500;

    private final DiscordGateway gateway;

    public DiscordAppReplySink(DiscordGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public void respondImmediately(OutboundResponse response, ReplyTarget target) {
        String content = response.getText().orElse("");
        if (response.getIntent().isPresent() && getCapabilities().supportsIntent(response.getIntent().get())) {
            // Phase 1: render intent as text; components can be added later (Discord V2)
            content = content.isEmpty() ? intentToText(response.getIntent().get()) : content;
        }
        if (content.isEmpty()) {
            return;
        }
        if (target instanceof ChannelTarget ct) {
            gateway.send(ct.channelId(), ct.messageId(), content);
            return;
        }
        if (target instanceof InteractionTarget it) {
            if (it.alreadyDeferred()) {
                gateway.sendFollowUp(it.token(), content);
            } else {
                gateway.sendFollowUp(it.token(), content);
            }
        }
    }

    @Override
    public void sendFollowUp(OutboundResponse response, ReplyTarget target) {
        if (!(target instanceof InteractionTarget it)) {
            log.debug("sendFollowUp requires InteractionTarget");
            return;
        }
        String content = response.getText().orElse("");
        if (response.getIntent().isPresent() && getCapabilities().supportsIntent(response.getIntent().get())) {
            content = content.isEmpty() ? intentToText(response.getIntent().get()) : content;
        }
        if (!content.isEmpty()) {
            gateway.sendFollowUp(it.token(), content);
        }
    }

    @Override
    public void updateMessage(OutboundResponse response, ReplyTarget target) {
        if (!(target instanceof InteractionTarget it)) {
            log.debug("updateMessage requires InteractionTarget");
            return;
        }
        String content = response.getText().orElse("");
        if (response.getIntent().isPresent() && getCapabilities().supportsIntent(response.getIntent().get())) {
            content = content.isEmpty() ? intentToText(response.getIntent().get()) : content;
        }
        if (!content.isEmpty()) {
            gateway.updateMessage(it.token(), content);
        }
    }

    @Override
    public void openModal(OutboundResponse response, ReplyTarget target) {
        if (!(target instanceof InteractionTarget it) || response.getIntent().isEmpty()) {
            return;
        }
        if (!getCapabilities().supportsModalInput()) {
            String text = response.getText().orElse(intentToText(response.getIntent().get()));
            gateway.sendFollowUp(it.token(), text);
            return;
        }
        // Phase 1: modal not implemented; fallback to text
        String text = response.getText().orElse(intentToText(response.getIntent().get()));
        gateway.sendFollowUp(it.token(), text);
    }

    @Override
    public Capabilities getCapabilities() {
        return new Capabilities(
                Set.of(
                        ResponseIntentType.PRESENT_CHOICES,
                        ResponseIntentType.CONFIRM_ACTION,
                        ResponseIntentType.COLLECT_TEXT,
                        ResponseIntentType.COLLECT_FORM,
                        ResponseIntentType.SHOW_ACTIONS,
                        ResponseIntentType.SHOW_STATUS
                ),
                true,
                true,
                true,
                false,
                DISCORD_DEFER_MS,
                25,
                5,
                5
        );
    }

    private static String intentToText(ResponseIntent intent) {
        return switch (intent) {
            case PresentChoices p -> p.prompt() != null ? p.prompt() : "Choose an option";
            case ConfirmAction c -> c.prompt() != null ? c.prompt() : "Confirm?";
            case CollectText c -> c.prompt() != null ? c.prompt() : "Enter text";
            case CollectForm c -> c.title() != null ? c.title() : "Form";
            case ShowActions s -> s.prompt() != null ? s.prompt() : "Actions";
            case ShowStatus s -> s.statusText() != null ? s.statusText() : "";
        };
    }
}
