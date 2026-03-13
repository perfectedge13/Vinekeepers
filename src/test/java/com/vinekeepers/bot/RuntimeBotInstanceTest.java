package com.vinekeepers.bot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RuntimeBotInstanceTest {

    @Test
    void constructorAndGetters() {
        RuntimeBotInstance instance = new RuntimeBotInstance(
                "arrietty-abc123", "arrietty", "Luna's helper", "chan-1");
        assertEquals("arrietty-abc123", instance.getInstanceId());
        assertEquals("arrietty", instance.getTemplateBotId());
        assertEquals("Luna's helper", instance.getDisplayName());
        assertEquals("chan-1", instance.getChannelId());
    }

    @Test
    void displayNameDefaultsToTemplateBotIdWhenNull() {
        RuntimeBotInstance instance = new RuntimeBotInstance("id", "arrietty", null, "chan-1");
        assertEquals("arrietty", instance.getDisplayName());
    }

    @Test
    void channelIdCanBeNull() {
        RuntimeBotInstance instance = new RuntimeBotInstance("id", "template", "Name", null);
        assertNull(instance.getChannelId());
    }

    @Test
    void constructorRejectsNullInstanceId() {
        assertThrows(NullPointerException.class,
                () -> new RuntimeBotInstance(null, "template", "Name", "chan"));
    }

    @Test
    void constructorRejectsNullTemplateBotId() {
        assertThrows(NullPointerException.class,
                () -> new RuntimeBotInstance("id", null, "Name", "chan"));
    }
}
