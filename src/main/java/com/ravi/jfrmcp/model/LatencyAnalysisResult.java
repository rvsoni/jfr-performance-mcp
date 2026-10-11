package com.ravi.jfrmcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Collections;
import java.util.List;

/**
 * Results of latency investigation identifying long-running pause, contention, and wait events exceeding a threshold.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LatencyAnalysisResult(
    String recording,
    double thresholdMs,
    long totalExceedingEvents,
    List<LongRunningEvent> longRunningEvents,
    List<PerformanceFinding> findings,
    String disclaimer
) {
    public LatencyAnalysisResult {
        longRunningEvents = longRunningEvents != null ? List.copyOf(longRunningEvents) : Collections.emptyList();
        findings = findings != null ? List.copyOf(findings) : Collections.emptyList();
    }

    public record LongRunningEvent(
        String eventType,
        double durationMs,
        String timestamp,
        String threadName,
        String details,
        List<String> sampleStackTrace
    ) {}
}
