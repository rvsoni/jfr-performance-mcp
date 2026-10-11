package com.ravi.jfrmcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Collections;
import java.util.List;

/**
 * Results of Garbage Collection and heap analysis from JFR events.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GcAnalysisResult(
    String recording,
    int gcCount,
    double totalPauseMs,
    double maxPauseMs,
    double averagePauseMs,
    int youngGcCount,
    int oldGcCount,
    Long heapUsedBeforeGcBytes,
    Long heapUsedAfterGcBytes,
    Long heapMaxBytes,
    Double heapOccupancyPercentAfterGc,
    String assessment,
    String assessmentRules,
    List<GcPauseEvent> longestPauses,
    List<PerformanceFinding> findings
) {
    public GcAnalysisResult {
        longestPauses = longestPauses != null ? List.copyOf(longestPauses) : Collections.emptyList();
        findings = findings != null ? List.copyOf(findings) : Collections.emptyList();
    }

    public record GcPauseEvent(
        String gcName,
        String cause,
        double durationMs,
        String timestamp
    ) {}
}
