package com.vinekeepers.core.cursor;

import java.net.URI;

/**
 * Thin transport seam so the adapter can be tested without real HTTP calls.
 */
public interface CursorCloudTransport {

    CursorCloudTransportResponse exchange(String method, URI uri, String bearerToken, String body) throws Exception;
}
