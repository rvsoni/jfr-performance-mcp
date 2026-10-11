package com.ravi.jfrmcp.exception;

/**
 * Thrown when access to a recording is denied (e.g. path traversal violation or permission error).
 */
public class RecordingAccessException extends JfrMcpException {

    public RecordingAccessException(String message) {
        super("RECORDING_ACCESS_DENIED", message);
    }

    public RecordingAccessException(String message, Throwable cause) {
        super("RECORDING_ACCESS_DENIED", message, cause);
    }
}
