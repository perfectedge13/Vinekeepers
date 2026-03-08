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
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * JDA-backed Discord gateway used by the app at runtime.
 */
public final class JdaDiscordGateway implements DiscordGateway {

    private static final Logger log = LoggerFactory.getLogger(JdaDiscordGateway.class);

    private final String token;
    private volatile JDA jda;
    private volatile boolean connected;

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
            jda = JDABuilder.createDefault(token)
                    .enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT, GatewayIntent.DIRECT_MESSAGES)
                    .addEventListeners(new ListenerAdapter() {
                        @Override
                        public void onMessageReceived(@NotNull MessageReceivedEvent event) {
                            if (event.getAuthor().isBot()) {
                                return;
                            }
                            publisher.accept(toEvent(event));
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
            throw e;
        }
    }

    @Override
    public void shutdown() {
        if (jda != null) {
            jda.shutdownNow();
        }
        connected = false;
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
