package com.vinekeepers.connectors;

import com.vinekeepers.events.Event;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.ItemComponent;
import net.dv8tion.jda.api.interactions.components.LayoutComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * JDA-backed Discord gateway used by the app at runtime.
 */
public final class JdaDiscordGateway implements DiscordGateway {

    private static final Logger log = LoggerFactory.getLogger(JdaDiscordGateway.class);

    /** Timeout for createTextChannel submit().get() — safe from callback threads. */
    private static final int CREATE_CHANNEL_TIMEOUT_SECONDS = 15;

    private final String token;
    private final boolean outboundOnly;
    private volatile JDA jda;
    private volatile boolean connected;
    private volatile Consumer<Event> publisher;
    /** Token -> InteractionHook for deferred interactions (adapter-owned timing). */
    private final Map<String, net.dv8tion.jda.api.interactions.InteractionHook> tokenToHook = new ConcurrentHashMap<>();

    public JdaDiscordGateway(String token) {
        this(token, false);
    }

    /**
     * @param outboundOnly when true, do not add event listeners (gateway is used for outbound only, e.g. non-routed bots).
     */
    public JdaDiscordGateway(String token, boolean outboundOnly) {
        this.token = token != null ? token.trim() : "";
        this.outboundOnly = outboundOnly;
    }

    @Override
    public void connect(Consumer<Event> publisher) {
        if (token.isBlank()) {
            log.warn("Discord token is not configured; Discord connector disabled.");
            connected = false;
            return;
        }
        try {
            this.publisher = publisher;
            JDABuilder builder = JDABuilder.createDefault(token)
                    .enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT, GatewayIntent.DIRECT_MESSAGES);
            if (!outboundOnly) {
                builder.addEventListeners(new ListenerAdapter() {
                    @Override
                    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
                        if (event.getAuthor().isBot()) {
                            return;
                        }
                        JdaDiscordGateway.this.publisher.accept(toEvent(event));
                    }
                    @Override
                    public void onGenericInteractionCreate(@NotNull GenericInteractionCreateEvent event) {
                        handleInteraction(event);
                    }
                });
            }
            jda = builder.build().awaitReady();
            connected = true;
            log.info("Discord gateway connected" + (outboundOnly ? " (outbound-only)" : ""));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            connected = false;
            throw new IllegalStateException("Discord gateway startup interrupted.", e);
        } catch (RuntimeException e) {
            connected = false;
            String msg = e.getMessage() != null ? e.getMessage() : "";
            Throwable cause = e.getCause();
            String causeMsg = cause != null && cause.getMessage() != null ? cause.getMessage() : "";
            if (msg.contains("4014") || msg.contains("Disallowed intents") || causeMsg.contains("4014") || causeMsg.contains("Disallowed intents")) {
                log.warn("Discord connection failed: enable Message Content Intent in Discord Developer Portal → Your App → Bot → Privileged Gateway Intents. See https://discord.com/developers/applications. Continuing without Discord.");
                return;
            }
            throw e;
        }
    }

    @Override
    public void shutdown() {
        tokenToHook.clear();
        if (jda != null) {
            jda.shutdownNow();
        }
        connected = false;
    }

    private void handleInteraction(GenericInteractionCreateEvent event) {
        if (event.getUser().isBot()) {
            return;
        }
        if (!(event instanceof IReplyCallback replyCallback)) {
            log.debug("Interaction not replyable, skipping");
            return;
        }
        // Adapter-owned timing: ack or defer within Discord's window (3s).
        replyCallback.deferReply().queue(
                success -> {
                    try {
                        String tok = event.getInteraction().getToken();
                        tokenToHook.put(tok, replyCallback.getHook());
                        Event ev = toInteractionEvent(event);
                        if (publisher != null) {
                            publisher.accept(ev);
                        }
                    } catch (Exception e) {
                        log.warn("Discord interaction publish failed: {}", e.getMessage());
                    }
                },
                failure -> log.warn("Discord defer failed: {}", failure.getMessage())
        );
    }

    private static Event toInteractionEvent(GenericInteractionCreateEvent event) {
        net.dv8tion.jda.api.interactions.Interaction interaction = event.getInteraction();
        Guild guild = interaction.getGuild();
        String sourceId = guild != null ? "discord:" + guild.getId() : "discord:dm";
        String channelId = interaction.getChannel() != null ? interaction.getChannel().getId() : "";
        String messageId = "";
        if (event instanceof net.dv8tion.jda.api.events.interaction.component.GenericComponentInteractionCreateEvent comp) {
            if (comp.getMessage() != null) {
                messageId = comp.getMessage().getId();
            }
        }
        String authorId = interaction.getUser() != null ? interaction.getUser().getId() : "";
        String author = interaction.getUser() != null && interaction.getUser().getName() != null ? interaction.getUser().getName() : "";
        String customId = null;
        Map<String, Object> values = Map.of();
        if (event instanceof net.dv8tion.jda.api.events.interaction.component.GenericComponentInteractionCreateEvent componentEvent) {
            customId = componentEvent.getComponentId();
            if (event instanceof StringSelectInteractionEvent selectEvent && selectEvent.getValues() != null && !selectEvent.getValues().isEmpty()) {
                values = Map.of("values", selectEvent.getValues());
            }
        }
        return new Event(sourceId, "interaction", Map.of(
                "channelId", channelId,
                "messageId", messageId,
                "authorId", authorId,
                "author", author,
                "interactionId", interaction.getId(),
                "token", interaction.getToken(),
                "deferred", true,
                "customId", customId != null ? customId : "",
                "values", values
        ));
    }

    @Override
    public void sendFollowUp(String token, String content) {
        sendFollowUp(token, content, null);
    }

    @Override
    public void sendFollowUp(String token, String content, List<List<Map<String, Object>>> components) {
        if (token == null || token.isBlank()) {
            return;
        }
        net.dv8tion.jda.api.interactions.InteractionHook hook = tokenToHook.get(token);
        if (hook == null) {
            log.warn("No deferred hook for token; cannot send follow-up");
            return;
        }
        String text = (content != null && !content.isBlank()) ? content : "(no content)";
        List<LayoutComponent> actionRows = toJdaActionRows(components);
        if (actionRows != null && !actionRows.isEmpty()) {
            hook.sendMessage(text).addComponents(actionRows).queue(
                    m -> { },
                    err -> log.debug("Discord follow-up failed: {}", err.getMessage()));
        } else {
            hook.sendMessage(text).queue(
                    m -> { },
                    err -> log.debug("Discord follow-up failed: {}", err.getMessage()));
        }
    }

    @Override
    public void updateMessage(String token, String content) {
        updateMessage(token, content, null);
    }

    @Override
    public void updateMessage(String token, String content, List<List<Map<String, Object>>> components) {
        if (token == null || token.isBlank()) {
            return;
        }
        net.dv8tion.jda.api.interactions.InteractionHook hook = tokenToHook.get(token);
        if (hook == null) {
            log.warn("No deferred hook for token; cannot update message");
            return;
        }
        String text = (content != null && !content.isBlank()) ? content : "(no content)";
        List<LayoutComponent> actionRows = toJdaActionRows(components);
        if (actionRows != null && !actionRows.isEmpty()) {
            hook.editOriginal(text).setComponents(actionRows).queue(
                    m -> { },
                    err -> log.debug("Discord update message failed: {}", err.getMessage()));
        } else {
            hook.editOriginal(text).queue(
                    m -> { },
                    err -> log.debug("Discord update message failed: {}", err.getMessage()));
        }
    }

    private static List<LayoutComponent> toJdaActionRows(List<List<Map<String, Object>>> components) {
        if (components == null || components.isEmpty()) return null;
        List<LayoutComponent> rows = new ArrayList<>();
        for (List<Map<String, Object>> row : components) {
            if (row == null || row.isEmpty()) continue;
            List<ItemComponent> comps = new ArrayList<>();
            for (Map<String, Object> m : row) {
                String type = m != null ? (String) m.get("type") : null;
                if ("button".equals(type)) {
                    String customId = m.get("custom_id") != null ? m.get("custom_id").toString() : "btn";
                    String label = m.get("label") != null ? m.get("label").toString() : "Button";
                    if (label.length() > 80) label = label.substring(0, 80);
                    comps.add(Button.primary(customId, label));
                } else if ("select_menu".equals(type)) {
                    String customId = m.get("custom_id") != null ? m.get("custom_id").toString() : "select";
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> options = (List<Map<String, Object>>) m.get("options");
                    if (options != null && !options.isEmpty()) {
                        StringSelectMenu.Builder menu = StringSelectMenu.create(customId);
                        int count = 0;
                        for (Map<String, Object> opt : options) {
                            if (count >= 25) break;
                            String value = opt.get("value") != null ? opt.get("value").toString() : "";
                            String label = opt.get("label") != null ? opt.get("label").toString() : value;
                            if (value.length() > 100) value = value.substring(0, 100);
                            if (label.length() > 100) label = label.substring(0, 100);
                            menu.addOption(label, value);
                            count++;
                        }
                        comps.add(menu.build());
                    }
                }
            }
            if (!comps.isEmpty()) {
                rows.add(ActionRow.of(comps));
            }
        }
        return rows.isEmpty() ? null : rows;
    }

    @Override
    public void send(String channelId, String messageId, String content) {
        send(channelId, messageId, content, null);
    }

    @Override
    public void send(String channelId, String messageId, String content, List<List<Map<String, Object>>> components) {
        if (!connected || jda == null || channelId == null || channelId.isBlank() || content == null || content.isBlank()) {
            return;
        }
        MessageChannel channel = jda.getChannelById(MessageChannel.class, channelId);
        if (channel == null) {
            log.warn("Discord channel {} not found for reply", channelId);
            return;
        }
        List<LayoutComponent> actionRows = toJdaActionRows(components);
        String text = content;
        if (messageId != null && !messageId.isBlank()) {
            channel.retrieveMessageById(messageId)
                    .flatMap(message -> {
                        var req = message.reply(text);
                        if (actionRows != null && !actionRows.isEmpty()) {
                            req = req.addComponents(actionRows);
                        }
                        return req;
                    })
                    .queue(
                            success -> { },
                            error -> {
                                log.debug("Discord reply fallback for {}: {}", messageId, error.getMessage());
                                var req = channel.sendMessage(text);
                                if (actionRows != null && !actionRows.isEmpty()) {
                                    req = req.addComponents(actionRows);
                                }
                                req.queue();
                            }
                    );
            return;
        }
        if (actionRows != null && !actionRows.isEmpty()) {
            channel.sendMessage(text).addComponents(actionRows).queue();
        } else {
            channel.sendMessage(text).queue();
        }
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public String getSelfUserId() {
        if (!connected || jda == null) {
            return null;
        }
        try {
            User self = jda.getSelfUser();
            return self != null ? self.getId() : null;
        } catch (Exception e) {
            log.debug("getSelfUserId failed: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public boolean addPermissionOverride(String channelId, String guildId, String targetUserId, long allow, long deny) {
        if (!connected || jda == null || channelId == null || channelId.isBlank()
                || guildId == null || guildId.isBlank() || targetUserId == null || targetUserId.isBlank()) {
            return false;
        }
        try {
            Guild guild = jda.getGuildById(guildId);
            if (guild == null) {
                log.warn("Discord guild {} not found for addPermissionOverride", guildId);
                return false;
            }
            TextChannel channel = guild.getTextChannelById(channelId);
            if (channel == null) {
                log.warn("Discord channel {} not found for addPermissionOverride", channelId);
                return false;
            }
            Member member = guild.retrieveMemberById(targetUserId)
                    .submit()
                    .get(CREATE_CHANNEL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (member == null) {
                log.warn("Discord member {} not found in guild {} for addPermissionOverride", targetUserId, guildId);
                return false;
            }
            Set<Permission> allowSet = allow != 0 ? EnumSet.copyOf(Permission.getPermissions(allow)) : EnumSet.noneOf(Permission.class);
            Set<Permission> denySet = deny != 0 ? EnumSet.copyOf(Permission.getPermissions(deny)) : EnumSet.noneOf(Permission.class);
            channel.getManager().putPermissionOverride(member, allowSet, denySet)
                    .submit()
                    .get(CREATE_CHANNEL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("addPermissionOverride interrupted: {}", e.getMessage());
            return false;
        } catch (TimeoutException e) {
            log.warn("addPermissionOverride timeout for member {} in guild {}", targetUserId, guildId);
            return false;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            log.warn("addPermissionOverride failed: {}", cause != null ? cause.getMessage() : e.getMessage());
            return false;
        } catch (Exception e) {
            log.warn("addPermissionOverride failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String createTextChannel(String guildId, String channelName) {
        if (!connected || jda == null || guildId == null || guildId.isBlank()) {
            return null;
        }
        String name = (channelName != null && !channelName.isBlank()) ? channelName.trim() : "lifecycle-room";
        if (name.length() > 100) {
            name = name.substring(0, 100);
        }
        try {
            Guild guild = jda.getGuildById(guildId);
            if (guild == null) {
                log.warn("Discord guild {} not found for createTextChannel", guildId);
                return null;
            }
            // submit().get(timeout) is safe from callback threads; complete() is not.
            TextChannel channel = guild.createTextChannel(name)
                    .submit()
                    .get(CREATE_CHANNEL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return channel != null ? channel.getId() : null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Discord createTextChannel failed: {}", e.getMessage());
            return null;
        } catch (TimeoutException e) {
            log.warn("Discord createTextChannel failed: timeout after {}s", CREATE_CHANNEL_TIMEOUT_SECONDS);
            return null;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            log.warn("Discord createTextChannel failed: {}", cause != null ? cause.getMessage() : e.getMessage());
            return null;
        } catch (Exception e) {
            log.warn("Discord createTextChannel failed: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public String createThreadChannel(String parentChannelId, String threadName) {
        if (!connected || jda == null || parentChannelId == null || parentChannelId.isBlank()) {
            return null;
        }
        String name = (threadName != null && !threadName.isBlank()) ? threadName.trim() : "Room updates";
        if (name.length() > 100) {
            name = name.substring(0, 100);
        }
        try {
            TextChannel parent = jda.getTextChannelById(parentChannelId);
            if (parent == null) {
                log.warn("Discord parent channel {} not found for createThreadChannel", parentChannelId);
                return null;
            }
            Message anchorMessage = parent.sendMessage(name).submit()
                    .get(CREATE_CHANNEL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (anchorMessage == null) {
                return null;
            }
            ThreadChannel thread = anchorMessage.createThreadChannel(name).submit()
                    .get(CREATE_CHANNEL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return thread != null ? thread.getId() : null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Discord createThreadChannel failed: {}", e.getMessage());
            return null;
        } catch (TimeoutException e) {
            log.warn("Discord createThreadChannel failed: timeout after {}s", CREATE_CHANNEL_TIMEOUT_SECONDS);
            return null;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            log.warn("Discord createThreadChannel failed: {}", cause != null ? cause.getMessage() : e.getMessage());
            return null;
        } catch (Exception e) {
            log.warn("Discord createThreadChannel failed: {}", e.getMessage());
            return null;
        }
    }

    private static Event toEvent(MessageReceivedEvent event) {
        Message message = event.getMessage();
        Guild guild = event.getGuild();
        String sourceId = guild != null ? "discord:" + guild.getId() : "discord:dm";
        String threadId = event.isFromThread() ? event.getChannel().getId() : null;
        Set<String> mentions = new LinkedHashSet<>();
        for (User user : message.getMentions().getUsers()) {
            collectMentionToken(mentions, user.getId());
            collectMentionToken(mentions, user.getName());
            collectMentionToken(mentions, user.getGlobalName());
        }
        for (Member member : message.getMentions().getMembers()) {
            if (member != null) {
                collectMentionToken(mentions, member.getEffectiveName());
            }
        }
        return new Event(sourceId, "message", Map.of(
                "channelId", event.getChannel().getId(),
                "threadId", threadId != null ? threadId : "",
                "authorId", event.getAuthor().getId(),
                "author", event.getAuthor().getName() != null ? event.getAuthor().getName() : "",
                "messageId", message.getId(),
                "content", message.getContentRaw(),
                "text", message.getContentRaw(),
                "mentions", mentions.stream().toList()
        ));
    }

    private static void collectMentionToken(Set<String> mentions, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        mentions.add(value.trim().toLowerCase(Locale.ROOT));
    }
}
