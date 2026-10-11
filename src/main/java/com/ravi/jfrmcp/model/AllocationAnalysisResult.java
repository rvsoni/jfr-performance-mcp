package com.ravi.jfrmcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Collections;
import java.util.List;

/**
 * Results of memory allocation analysis from JFR allocation events.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AllocationAnalysisResult(
    String recording,
    long totalAllocationSamples,
    long estimatedTotalAllocatedBytes,
    Double estimatedAllocationRateMbPerSec,
    List<AllocatingClass> topAllocatingClasses,
    List<ThreadAllocation> topAllocatingThreads,
    List<PerformanceFinding> findings,
    String estimationNote
) {
    public AllocationAnalysisResult {
        topAllocatingClasses = topAllocatingClasses != null ? List.copyOf(topAllocatingClasses) : Collections.emptyList();
        topAllocatingThreads = topAllocatingThreads != null ? List.copyOf(topAllocatingThreads) : Collections.emptyList();
        findings = findings != null ? List.copyOf(findings) : Collections.emptyList();
    }

    public record AllocatingClass(
        String className,
        long sampleCount,
        long estimatedBytes,
        double percentage
    ) {}

    public record ThreadAllocation(
        String threadName,
        long sampleCount,
        long estimatedBytes,
        double percentage
    ) {}
}
