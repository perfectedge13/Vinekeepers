package com.vinekeepers.connectors;

/**
 * Reason for create-room (e.g. create channel) failure from SpaceOperations.
 */
public enum CreateRoomFailureReason {
    REQUEST_NULL,
    GATEWAY_UNAVAILABLE,
    GUILD_ID_MISSING,
    CREATE_RETURNED_NULL,
    OWNER_USER_ID_NULL,
    PERMISSION_OVERRIDE_FAILED,
    EXCEPTION
}
