package com.ravi.jfrmcp.exception;

/**
 * Thrown when a file is corrupted, malformed, or not a valid JFR recording.
 */
public class InvalidRecordingException extends JfrMcpException {

    public InvalidRecordingException(String message) {
        super("INVALID_RECORDING", message);
    }

    public InvalidRecordingException(String message, Throwable cause) {
        super("INVALID_RECORDING", message, cause);
    }
}
