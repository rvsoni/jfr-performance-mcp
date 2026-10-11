package com.ravi.jfrmcp.exception;

/**
 * Base exception for all JFR MCP domain exceptions.
 */
public class JfrMcpException extends RuntimeException {

    private final String errorCode;

    public JfrMcpException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public JfrMcpException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
