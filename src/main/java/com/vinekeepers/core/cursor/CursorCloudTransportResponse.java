package com.vinekeepers.core.cursor;

/**
 * Raw HTTP response from the Cursor transport layer.
 */
public record CursorCloudTransportResponse(
        int statusCode,
        String body
) {
}
