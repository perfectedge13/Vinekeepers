package com.vinekeepers.workflow;

import com.vinekeepers.events.Event;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class SessionKeyStrategiesTest {

    private static final String THREAD = "thread-abc";
    private static final String USER = "user-42";

    @Test
    void threadStrategy_messageWithThreadId_matchesInteractionWithThreadId() {
        Event message = new Event("discord:g", "message", Map.of(
                "channelId", THREAD,
                "threadId", THREAD,
                "authorId", USER,
                "content", "hi"));
        Event interaction = new Event("discord:g", "interaction", Map.of(
                "channelId", THREAD,
                "threadId", THREAD,
                "authorId", USER,
                "customId", "approve",
                "interactionId", "i1",
                "token", "tok"));

        String km = SessionKeyStrategies.resolve("thread", message).resolveSessionKey("arrietty", message);
        String ki = SessionKeyStrategies.resolve("thread", interaction).resolveSessionKey("arrietty", interaction);
        assertEquals("bot:arrietty:conv:" + THREAD, km);
        assertEquals(km, ki);
    }

    @Test
    void threadStrategy_interactionWithoutThreadId_usesChannelUserKey_notThreadKey() {
        Event message = new Event("discord:g", "message", Map.of(
                "channelId", THREAD,
                "threadId", THREAD,
                "authorId", USER,
                "content", "hi"));
        Event interactionNoThread = new Event("discord:g", "interaction", Map.of(
                "channelId", THREAD,
                "authorId", USER,
                "customId", "approve",
                "interactionId", "i1",
                "token", "tok"));

        String kMsg = SessionKeyStrategies.resolve("thread", message).resolveSessionKey("arrietty", message);
        String kInt = SessionKeyStrategies.resolve("thread", interactionNoThread)
                .resolveSessionKey("arrietty", interactionNoThread);
        assertEquals("bot:arrietty:conv:" + THREAD, kMsg);
        assertNotEquals(kMsg, kInt);
        assertEquals("bot:arrietty:conv:" + THREAD + ":" + USER, kInt);
    }
}
