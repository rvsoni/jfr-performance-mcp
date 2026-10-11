package com.ravi.jfrmcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Collections;
import java.util.List;

/**
 * Results of JFR CPU profiling and execution sampling analysis.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CpuAnalysisResult(
    String recording,
    long totalSamples,
    List<MethodSample> topMethods,
    List<PackageSample> topPackages,
    List<ClassSample> topClasses,
    List<ThreadCpuSample> topThreads,
    Double averageJvmUserCpu,
    Double averageMachineCpu,
    List<PerformanceFinding> findings,
    String evidenceNote
) {
    public CpuAnalysisResult {
        topMethods = topMethods != null ? List.copyOf(topMethods) : Collections.emptyList();
        topPackages = topPackages != null ? List.copyOf(topPackages) : Collections.emptyList();
        topClasses = topClasses != null ? List.copyOf(topClasses) : Collections.emptyList();
        topThreads = topThreads != null ? List.copyOf(topThreads) : Collections.emptyList();
        findings = findings != null ? List.copyOf(findings) : Collections.emptyList();
    }

    public record MethodSample(
        String method,
        long samples,
        double percentage,
        boolean isNative,
        List<String> sampleStackTrace
    ) {}

    public record PackageSample(
        String packageName,
        long samples,
        double percentage
    ) {}

    public record ClassSample(
        String className,
        long samples,
        double percentage
    ) {}

    public record ThreadCpuSample(
        String threadName,
        long samples,
        double percentage
    ) {}
}
