package com.ravi.jfrmcp.exception;

/**
 * Thrown when a recording format, feature, or version is unsupported.
 */
public class UnsupportedRecordingException extends JfrMcpException {

    public UnsupportedRecordingException(String message) {
        super("UNSUPPORTED_RECORDING", message);
    }

    public UnsupportedRecordingException(String message, Throwable cause) {
        super("UNSUPPORTED_RECORDING", message, cause);
    }
}
