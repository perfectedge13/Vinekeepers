package com.vinekeepers.connectors;

/**
 * Connector-specific "space" operations (e.g. create room/channel, create thread).
 * Implementations are resolved by source prefix (e.g. "discord") from SpaceOperationsRegistry.
 */
public interface SpaceOperations {

    /**
     * Create a room (e.g. Discord text channel) for the given request (explicit intent fields).
     * Returns typed result: success with channel id or failure with reason.
     */
    CreateRoomResult createRoom(CreateRoomRequest request);

    /**
     * Create a thread under a parent room for the given request (explicit intent fields).
     * Returns typed result: success with thread id or failure with reason.
     */
    CreateThreadResult createThread(CreateThreadRequest request);
}
