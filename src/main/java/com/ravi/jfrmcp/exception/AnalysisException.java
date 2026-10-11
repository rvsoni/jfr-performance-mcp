package com.ravi.jfrmcp.exception;

/**
 * Thrown when an error occurs during JFR performance analysis.
 */
public class AnalysisException extends JfrMcpException {

    public AnalysisException(String message) {
        super("ANALYSIS_ERROR", message);
    }

    public AnalysisException(String message, Throwable cause) {
        super("ANALYSIS_ERROR", message, cause);
    }
}
