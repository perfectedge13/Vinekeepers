package com.vinekeepers.connectors;

/**
 * Reason for create-thread failure from SpaceOperations.
 */
public enum CreateThreadFailureReason {
    ROUTER_NULL,
    REQUEST_NULL,
    CHANNEL_ID_MISSING,
    GATEWAY_UNAVAILABLE,
    CREATE_RETURNED_NULL
}
