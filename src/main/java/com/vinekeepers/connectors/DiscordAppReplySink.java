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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    private final OutboundDeliveryRouter router;

    /** Single-gateway sink (e.g. tests). */
    public DiscordAppReplySink(DiscordGateway gateway) {
        this.gateway = gateway;
        this.router = null;
    }

    /** Router-based sink: resolves gateway by channel from lifecycle context. */
    public DiscordAppReplySink(OutboundDeliveryRouter router) {
        this.gateway = null;
        this.router = router;
    }

    /**
     * Resolves gateway for the target. When using router, casts to DiscordGateway — this sink intentionally
     * relies on DiscordGateway for interaction methods (sendFollowUp, updateMessage, openModal, send with components); accepted debt.
     */
    private DiscordGateway gatewayFor(ReplyTarget target) {
        if (router != null && target != null) {
            DiscordGateway gw = (DiscordGateway) router.getGatewayForChannel(target.channelId());
            if (gw == null) {
                gw = (DiscordGateway) router.getDefaultGateway();
            }
            return gw;
        }
        return gateway;
    }

    @Override
    public void respondImmediately(OutboundResponse response, ReplyTarget target) {
        DiscordGateway gw = gatewayFor(target);
        if (gw == null) {
            return;
        }
        String content = response.getText().orElse("");
        List<List<Map<String, Object>>> components = null;
        if (response.getIntent().isPresent() && getCapabilities().supportsIntent(response.getIntent().get())) {
            components = intentToComponents(response.getIntent().get());
            if (content.isEmpty()) {
                content = intentToText(response.getIntent().get());
            }
        }
        if (content.isEmpty()) {
            return;
        }
        if (target instanceof ChannelTarget ct) {
            gw.send(ct.channelId(), ct.messageId(), content, components);
            return;
        }
        if (target instanceof InteractionTarget it) {
            gw.sendFollowUp(it.token(), content, components);
        }
    }

    @Override
    public void sendFollowUp(OutboundResponse response, ReplyTarget target) {
        if (!(target instanceof InteractionTarget it)) {
            log.debug("sendFollowUp requires InteractionTarget");
            return;
        }
        DiscordGateway gw = gatewayFor(target);
        if (gw == null) {
            return;
        }
        String content = response.getText().orElse("");
        List<List<Map<String, Object>>> components = null;
        if (response.getIntent().isPresent() && getCapabilities().supportsIntent(response.getIntent().get())) {
            components = intentToComponents(response.getIntent().get());
            if (content.isEmpty()) {
                content = intentToText(response.getIntent().get());
            }
        }
        if (!content.isEmpty()) {
            gw.sendFollowUp(it.token(), content, components);
        }
    }

    @Override
    public void updateMessage(OutboundResponse response, ReplyTarget target) {
        if (!(target instanceof InteractionTarget it)) {
            log.debug("updateMessage requires InteractionTarget");
            return;
        }
        DiscordGateway gw = gatewayFor(target);
        if (gw == null) {
            return;
        }
        String content = response.getText().orElse("");
        List<List<Map<String, Object>>> components = null;
        if (response.getIntent().isPresent() && getCapabilities().supportsIntent(response.getIntent().get())) {
            components = intentToComponents(response.getIntent().get());
            if (content.isEmpty()) {
                content = intentToText(response.getIntent().get());
            }
        }
        if (!content.isEmpty()) {
            gw.updateMessage(it.token(), content, components);
        }
    }

    @Override
    public void openModal(OutboundResponse response, ReplyTarget target) {
        if (!(target instanceof InteractionTarget it) || response.getIntent().isEmpty()) {
            return;
        }
        DiscordGateway gw = gatewayFor(target);
        if (gw == null) {
            return;
        }
        if (!getCapabilities().supportsModalInput()) {
            String text = response.getText().orElse(intentToText(response.getIntent().get()));
            gw.sendFollowUp(it.token(), text);
            return;
        }
        // Phase 1: modal not implemented; fallback to text
        String text = response.getText().orElse(intentToText(response.getIntent().get()));
        gw.sendFollowUp(it.token(), text);
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

    /**
     * Build Discord component rows from intent. Buttons (max 5 per row) for small choice sets or ConfirmAction;
     * string select menu (max 25 options) for larger sets.
     */
    private static List<List<Map<String, Object>>> intentToComponents(ResponseIntent intent) {
        List<List<Map<String, Object>>> rows = new ArrayList<>();
        if (intent instanceof PresentChoices p && p.choices() != null && !p.choices().isEmpty()) {
            List<ResponseIntent.Choice> choices = p.choices();
            if (choices.size() <= 5 && choices.stream().allMatch(c -> (c.label() == null ? 0 : c.label().length()) <= 80)) {
                List<Map<String, Object>> row = new ArrayList<>();
                for (ResponseIntent.Choice c : choices) {
                    String id = c.id() != null ? c.id() : "";
                    String label = c.label() != null && !c.label().isBlank() ? c.label() : id;
                    if (label.length() > 80) label = label.substring(0, 80);
                    row.add(Map.of("type", "button", "custom_id", id, "label", label));
                }
                rows.add(row);
            } else {
                List<Map<String, Object>> options = new ArrayList<>();
                for (ResponseIntent.Choice c : choices) {
                    if (options.size() >= 25) break;
                    String id = c.id() != null ? c.id() : "";
                    String label = c.label() != null && !c.label().isBlank() ? c.label() : id;
                    if (label.length() > 100) label = label.substring(0, 100);
                    options.add(Map.of("value", id, "label", label));
                }
                rows.add(List.of(Map.of("type", "select_menu", "custom_id", "choice", "options", options)));
            }
        } else if (intent instanceof ConfirmAction c) {
            String confirmId = "confirm";
            String cancelId = "cancel";
            String confirmLabel = c.confirmLabel() != null && !c.confirmLabel().isBlank() ? c.confirmLabel() : "Confirm";
            String cancelLabel = c.cancelLabel() != null && !c.cancelLabel().isBlank() ? c.cancelLabel() : "Cancel";
            rows.add(List.of(
                    Map.of("type", "button", "custom_id", confirmId, "label", confirmLabel.length() > 80 ? confirmLabel.substring(0, 80) : confirmLabel),
                    Map.of("type", "button", "custom_id", cancelId, "label", cancelLabel.length() > 80 ? cancelLabel.substring(0, 80) : cancelLabel)
            ));
        }
        return rows.isEmpty() ? null : rows;
    }
}
