package com.ravi.jfrmcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * Architectural abstraction capturing key performance metrics of a recording snapshot,
 * designed for future differential and regression analysis (compare_recordings).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PerformanceSnapshot(
    String recordingName,
    Instant timestamp,
    double durationSeconds,
    double averageJvmCpuPercent,
    int totalGcCount,
    double totalGcPauseMs,
    double maxGcPauseMs,
    long totalAllocationBytes,
    double estimatedAllocationRateMbPerSec,
    int totalThreads,
    int blockedThreads,
    double lockContentionDurationMs,
    long exceptionCount,
    double totalIoDurationMs,
    Map<String, Double> topMethodSamplePercentages
) {
}
