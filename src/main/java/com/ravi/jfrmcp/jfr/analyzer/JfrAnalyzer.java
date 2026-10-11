package com.ravi.jfrmcp.jfr.analyzer;

import com.ravi.jfrmcp.jfr.JfrRecordingReader;

/**
 * Common interface for domain-specific JFR performance analyzers.
 * Analyzers process raw JFR events and produce structured domain analysis results.
 *
 * @param <R> the analysis result type
 */
public interface JfrAnalyzer<R> {

    /**
     * Unique identifier for this analyzer.
     */
    String getName();

    /**
     * Executes analysis over the events provided by the reader.
     *
     * @param reader streaming JFR reader
     * @return structured analysis result
     */
    R analyze(JfrRecordingReader reader);
}
