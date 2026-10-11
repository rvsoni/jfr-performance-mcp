package com.ravi.jfrmcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Collections;
import java.util.List;

/**
 * Correlated multi-dimensional analysis of a JFR recording.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FullAnalysisResult(
    String recording,
    FindingSeverity overallAssessment,
    List<PerformanceFinding> findings,
    List<String> healthyAreas,
    List<String> nextSteps,
    RecordingSummary summary,
    CpuAnalysisResult cpu,
    GcAnalysisResult gc,
    AllocationAnalysisResult allocation,
    ThreadAnalysisResult threads,
    LockAnalysisResult locks,
    ExceptionAnalysisResult exceptions,
    IoAnalysisResult io,
    LatencyAnalysisResult latency
) {
    public FullAnalysisResult {
        findings = findings != null ? List.copyOf(findings) : Collections.emptyList();
        healthyAreas = healthyAreas != null ? List.copyOf(healthyAreas) : Collections.emptyList();
        nextSteps = nextSteps != null ? List.copyOf(nextSteps) : Collections.emptyList();
    }
}
