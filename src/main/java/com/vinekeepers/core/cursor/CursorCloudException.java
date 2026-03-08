package com.vinekeepers.core.cursor;

/**
 * Runtime error returned by the Cursor cloud agent API.
 */
public final class CursorCloudException extends RuntimeException {

    private final String code;
    private final int statusCode;

    public CursorCloudException(String message) {
        this(message, null, 0, null);
    }

    public CursorCloudException(String message, Throwable cause) {
        this(message, null, 0, cause);
    }

    public CursorCloudException(String message, String code, int statusCode, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.statusCode = statusCode;
    }

    public String getCode() {
        return code;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
