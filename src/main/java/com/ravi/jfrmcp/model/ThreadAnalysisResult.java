package com.ravi.jfrmcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Collections;
import java.util.List;

/**
 * Results of thread activity and concurrency analysis.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ThreadAnalysisResult(
    String recording,
    int totalThreadCount,
    int liveThreadCount,
    int peakThreadCount,
    int blockedThreads,
    int waitingThreads,
    int parkedThreads,
    List<ThreadSummary> threads,
    List<ThreadCpuActivity> topCpuThreads,
    List<PerformanceFinding> findings
) {
    public ThreadAnalysisResult {
        threads = threads != null ? List.copyOf(threads) : Collections.emptyList();
        topCpuThreads = topCpuThreads != null ? List.copyOf(topCpuThreads) : Collections.emptyList();
        findings = findings != null ? List.copyOf(findings) : Collections.emptyList();
    }

    public record ThreadSummary(
        String threadName,
        String threadState,
        boolean isDaemon,
        long samples
    ) {}

    public record ThreadCpuActivity(
        String threadName,
        long cpuSamples,
        double percentage
    ) {}
}
