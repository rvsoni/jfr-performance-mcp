package com.ravi.jfrmcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Collections;
import java.util.List;

/**
 * Results of File and Socket I/O latency analysis from JFR events.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IoAnalysisResult(
    String recording,
    IoCategorySummary fileReads,
    IoCategorySummary fileWrites,
    IoCategorySummary socketReads,
    IoCategorySummary socketWrites,
    List<SlowIoEvent> slowestEvents,
    List<PerformanceFinding> findings
) {
    public IoAnalysisResult {
        slowestEvents = slowestEvents != null ? List.copyOf(slowestEvents) : Collections.emptyList();
        findings = findings != null ? List.copyOf(findings) : Collections.emptyList();
    }

    public record IoCategorySummary(
        long eventCount,
        long totalBytes,
        double totalDurationMs,
        double maxDurationMs,
        double avgDurationMs
    ) {}

    public record SlowIoEvent(
        String ioType,
        String target,
        long bytes,
        double durationMs,
        String threadName,
        String timestamp,
        List<String> sampleStackTrace
    ) {}
}
