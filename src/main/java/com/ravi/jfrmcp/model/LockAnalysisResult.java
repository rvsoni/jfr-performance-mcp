package com.ravi.jfrmcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Collections;
import java.util.List;

/**
 * Results of monitor and lock contention analysis from JFR synchronization events.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LockAnalysisResult(
    String recording,
    long totalContentionEvents,
    double totalBlockedDurationMs,
    List<LockContentionSummary> contentions,
    List<ContendedLocation> topLockLocations,
    List<PerformanceFinding> findings
) {
    public LockAnalysisResult {
        contentions = contentions != null ? List.copyOf(contentions) : Collections.emptyList();
        topLockLocations = topLockLocations != null ? List.copyOf(topLockLocations) : Collections.emptyList();
        findings = findings != null ? List.copyOf(findings) : Collections.emptyList();
    }

    public record LockContentionSummary(
        String monitorClass,
        int blockedThreads,
        long eventCount,
        double blockedTimeMs,
        double maxBlockedTimeMs,
        double avgBlockedTimeMs,
        List<String> contendingThreads,
        List<String> sampleStackTrace
    ) {}

    public record ContendedLocation(
        String location,
        long count,
        double totalDurationMs,
        List<String> sampleStackTrace
    ) {}
}
