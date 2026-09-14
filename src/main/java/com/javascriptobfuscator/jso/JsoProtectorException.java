package com.javascriptobfuscator.jso;

/**
 * Thrown when the JSO API rejects a request or the response is malformed.
 * The message is safe to log — API key / password values are never interpolated.
 */
public class JsoProtectorException extends RuntimeException {

    private final String type;
    private final String errorCode;

    public JsoProtectorException(String message, String type, String errorCode) {
        super(message);
        this.type = type;
        this.errorCode = errorCode;
    }

    /** Server-side {@code Type} field when present (e.g. "Error", "SourceError"). */
    public String type() { return type; }

    /** Server-side {@code ErrorCode} field when present (e.g. "AUTH_FAIL"). */
    public String errorCode() { return errorCode; }
}
