package com.vinekeepers.connectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ConnectorRegistry: register and get adapters by connector id.
 */
class ConnectorRegistryTest {

    private ConnectorRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ConnectorRegistry();
    }

    @Test
    void get_returnsNullWhenNotRegistered() {
        assertNull(registry.get("discord"));
        assertNull(registry.get("github"));
    }

    @Test
    void register_andGet_returnsAdapter() {
        ConnectorAdapter adapter = (bots, context) -> {};
        registry.register("discord", adapter);
        assertSame(adapter, registry.get("discord"));
    }

    @Test
    void register_overwritesPrevious() {
        ConnectorAdapter first = (bots, context) -> {};
        ConnectorAdapter second = (bots, context) -> {};
        registry.register("discord", first);
        registry.register("discord", second);
        assertSame(second, registry.get("discord"));
    }

    @Test
    void register_withNullOrBlankConnectorId_doesNotRegister() {
        ConnectorAdapter adapter = (bots, context) -> {};
        registry.register(null, adapter);
        registry.register("", adapter);
        registry.register("   ", adapter);
        assertNull(registry.get("discord"));
    }

    @Test
    void register_multipleConnectors() {
        ConnectorAdapter discord = (bots, context) -> {};
        ConnectorAdapter github = (bots, context) -> {};
        registry.register("discord", discord);
        registry.register("github", github);
        assertSame(discord, registry.get("discord"));
        assertSame(github, registry.get("github"));
    }
}
