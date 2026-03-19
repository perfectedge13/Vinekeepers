package com.vinekeepers.connectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Tests for SpaceOperationsRegistry: register and get SpaceOperations by connector id.
 */
class SpaceOperationsRegistryTest {

    private SpaceOperationsRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SpaceOperationsRegistry();
    }

    @Test
    void get_returnsNullWhenNotRegistered() {
        assertNull(registry.get("discord"));
        assertNull(registry.get("github"));
    }

    @Test
    void register_andGet_returnsOps() {
        SpaceOperations ops = stubOps("room-1", "thread-1");
        registry.register("discord", ops);
        assertSame(ops, registry.get("discord"));
    }

    @Test
    void register_overwritesPrevious() {
        SpaceOperations first = stubOps("r1", null);
        SpaceOperations second = stubOps("r2", null);
        registry.register("discord", first);
        registry.register("discord", second);
        assertSame(second, registry.get("discord"));
    }

    @Test
    void register_withNullOrBlankConnectorId_doesNotRegister() {
        SpaceOperations ops = stubOps(null, null);
        registry.register(null, ops);
        registry.register("", ops);
        registry.register("   ", ops);
        assertNull(registry.get("discord"));
    }

    @Test
    void register_withNullOps_doesNotStore() {
        registry.register("discord", null);
        assertNull(registry.get("discord"));
    }

    @Test
    void get_withNullOrBlankConnectorId_returnsNull() {
        SpaceOperations ops = stubOps(null, null);
        registry.register("discord", ops);
        assertNull(registry.get(null));
        assertNull(registry.get(""));
        assertNull(registry.get("   "));
    }

    @Test
    void get_trimmedConnectorId() {
        SpaceOperations ops = stubOps(null, null);
        registry.register("discord", ops);
        assertSame(ops, registry.get("  discord  "));
    }

    private static SpaceOperations stubOps(String roomId, String threadId) {
        return new SpaceOperations() {
            @Override
            public CreateRoomResult createRoom(CreateRoomRequest request) {
                return roomId != null ? new CreateRoomResult.Success(roomId) : new CreateRoomResult.Failure(CreateRoomFailureReason.GUILD_ID_MISSING);
            }
            @Override
            public CreateThreadResult createThread(CreateThreadRequest request) {
                return threadId != null ? new CreateThreadResult.Success(threadId) : new CreateThreadResult.Failure(CreateThreadFailureReason.CHANNEL_ID_MISSING);
            }
        };
    }
}
