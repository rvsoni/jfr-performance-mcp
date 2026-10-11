package com.ravi.jfrmcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Collections;
import java.util.List;

/**
 * Results of Java exception and error analysis from JFR events.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExceptionAnalysisResult(
    String recording,
    long totalExceptionsThrown,
    long totalErrors,
    List<ExceptionTypeCount> topExceptions,
    List<ThrowingMethod> topThrowingMethods,
    List<ThrowingThread> topThrowingThreads,
    List<PerformanceFinding> findings
) {
    public ExceptionAnalysisResult {
        topExceptions = topExceptions != null ? List.copyOf(topExceptions) : Collections.emptyList();
        topThrowingMethods = topThrowingMethods != null ? List.copyOf(topThrowingMethods) : Collections.emptyList();
        topThrowingThreads = topThrowingThreads != null ? List.copyOf(topThrowingThreads) : Collections.emptyList();
        findings = findings != null ? List.copyOf(findings) : Collections.emptyList();
    }

    public record ExceptionTypeCount(
        String exceptionClass,
        long count,
        String sampleMessage,
        boolean isError
    ) {}

    public record ThrowingMethod(
        String method,
        long count,
        List<String> sampleStackTrace
    ) {}

    public record ThrowingThread(
        String threadName,
        long count
    ) {}
}
