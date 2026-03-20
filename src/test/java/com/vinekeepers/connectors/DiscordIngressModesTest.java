package com.vinekeepers.connectors;

import com.vinekeepers.bot.BotDefinition;
import com.vinekeepers.bot.ConnectorIdentity;
import com.vinekeepers.bot.MemoryPolicy;
import com.vinekeepers.bot.ModelProfile;
import com.vinekeepers.bot.Persona;
import com.vinekeepers.bot.ToolPolicy;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordIngressModesTest {

    private static final Persona P = new Persona("T", "");
    private static final ModelProfile M = new ModelProfile("stub", "stub");

    @Test
    void routedOnly_fullMessageAndInteractionIngress() {
        BotDefinition luna = bot("luna", Map.of(
                "tokenEnvKey", "L",
                "handlesOwnedSpaces", false));
        DiscordIngressModes modes = DiscordIngressModes.resolve(luna, Set.of("luna"));
        assertEquals(DiscordMessageIngressMode.ROUTED, modes.messageMode());
        assertEquals(DiscordInteractionIngressMode.ROUTED, modes.interactionMode());
        assertFalse(modes.isOutboundOnly());
    }

    @Test
    void handlesOwnedOnly_scopedMessages_ownInteractions() {
        BotDefinition arrietty = bot("arrietty", Map.of(
                "tokenEnvKey", "A",
                "handlesOwnedSpaces", true));
        DiscordIngressModes modes = DiscordIngressModes.resolve(arrietty, Set.of("luna"));
        assertEquals(DiscordMessageIngressMode.OWNED_SPACES, modes.messageMode());
        assertEquals(DiscordInteractionIngressMode.OWN_MESSAGES, modes.interactionMode());
        assertFalse(modes.isOutboundOnly());
    }

    @Test
    void neitherRoutedNorHandles_outboundOnly() {
        BotDefinition b = bot("architect", Map.of("tokenEnvKey", "X", "handlesOwnedSpaces", false));
        DiscordIngressModes modes = DiscordIngressModes.resolve(b, Set.of("luna"));
        assertTrue(modes.isOutboundOnly());
    }

    @Test
    void yamlIngress_overridesDerived() {
        BotDefinition b = bot("arrietty", Map.of(
                "tokenEnvKey", "A",
                "handlesOwnedSpaces", true,
                "ingress", Map.of("messages", "none", "interactions", "own_messages")));
        DiscordIngressModes modes = DiscordIngressModes.resolve(b, Set.of("luna"));
        assertEquals(DiscordMessageIngressMode.NONE, modes.messageMode());
        assertEquals(DiscordInteractionIngressMode.OWN_MESSAGES, modes.interactionMode());
    }

    @Test
    void routedAndHandles_fullRouted() {
        BotDefinition b = bot("hybrid", Map.of(
                "tokenEnvKey", "H",
                "handlesOwnedSpaces", true));
        DiscordIngressModes modes = DiscordIngressModes.resolve(b, Set.of("hybrid", "luna"));
        assertEquals(DiscordMessageIngressMode.ROUTED, modes.messageMode());
        assertEquals(DiscordInteractionIngressMode.ROUTED, modes.interactionMode());
    }

    private static BotDefinition bot(String id, Map<String, Object> discordAttrs) {
        return new BotDefinition(id, P, M, ToolPolicy.allowAll(), new MemoryPolicy(4096),
                "stub", null, null, null,
                Map.of("discord", new ConnectorIdentity(discordAttrs)));
    }
}
