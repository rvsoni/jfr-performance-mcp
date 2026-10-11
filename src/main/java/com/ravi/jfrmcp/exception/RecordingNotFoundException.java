package com.ravi.jfrmcp.exception;

/**
 * Thrown when a requested JFR recording file cannot be found.
 */
public class RecordingNotFoundException extends JfrMcpException {

    public RecordingNotFoundException(String message) {
        super("RECORDING_NOT_FOUND", message);
    }

    public RecordingNotFoundException(String message, Throwable cause) {
        super("RECORDING_NOT_FOUND", message, cause);
    }
}
