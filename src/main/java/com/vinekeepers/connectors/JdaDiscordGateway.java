package com.vinekeepers.connectors;

import com.vinekeepers.events.Event;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * JDA-backed Discord gateway used by the app at runtime.
 */
public final class JdaDiscordGateway implements DiscordGateway {

    private static final Logger log = LoggerFactory.getLogger(JdaDiscordGateway.class);

    private final String token;
    private volatile JDA jda;
    private volatile boolean connected;
    private volatile Consumer<Event> publisher;
    /** Token -> InteractionHook for deferred interactions (adapter-owned timing). */
    private final Map<String, net.dv8tion.jda.api.interactions.InteractionHook> tokenToHook = new ConcurrentHashMap<>();

    public JdaDiscordGateway(String token) {
        this.token = token != null ? token.trim() : "";
    }

    @Override
    public void connect(Consumer<Event> publisher) {
        if (token.isBlank()) {
            log.warn("DISCORD_BOT_TOKEN is not configured; Discord connector disabled.");
            connected = false;
            return;
        }
        try {
            this.publisher = publisher;
            jda = JDABuilder.createDefault(token)
                    .enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT, GatewayIntent.DIRECT_MESSAGES)
                    .addEventListeners(new ListenerAdapter() {
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
                    })
                    .build()
                    .awaitReady();
            connected = true;
            log.info("Discord gateway connected");
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
        if (content != null && !content.isBlank()) {
            hook.sendMessage(content).queue(
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
        hook.editOriginal(text).queue(
                m -> { },
                err -> log.debug("Discord update message failed: {}", err.getMessage()));
    }

    @Override
    public void send(String channelId, String messageId, String content) {
        if (!connected || jda == null || channelId == null || channelId.isBlank() || content == null || content.isBlank()) {
            return;
        }
        MessageChannel channel = jda.getChannelById(MessageChannel.class, channelId);
        if (channel == null) {
            log.warn("Discord channel {} not found for reply", channelId);
            return;
        }
        if (messageId != null && !messageId.isBlank()) {
            channel.retrieveMessageById(messageId)
                    .flatMap(message -> message.reply(content))
                    .queue(
                            success -> { },
                            error -> {
                                log.debug("Discord reply fallback for {}: {}", messageId, error.getMessage());
                                channel.sendMessage(content).queue();
                            }
                    );
            return;
        }
        channel.sendMessage(content).queue();
    }

    @Override
    public boolean isConnected() {
        return connected;
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
