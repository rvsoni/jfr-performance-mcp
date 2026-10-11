package com.ravi.jfrmcp.jfr.analyzer;

import com.ravi.jfrmcp.jfr.JfrEventFilter;
import com.ravi.jfrmcp.jfr.JfrEventUtils;
import com.ravi.jfrmcp.jfr.JfrRecordingReader;
import com.ravi.jfrmcp.model.CpuAnalysisResult;
import com.ravi.jfrmcp.model.Evidence;
import com.ravi.jfrmcp.model.FindingCategory;
import com.ravi.jfrmcp.model.FindingSeverity;
import com.ravi.jfrmcp.model.PerformanceFinding;
import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedMethod;
import jdk.jfr.consumer.RecordedStackTrace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Analyzes CPU execution samples and CPU load metrics from JFR recordings.
 */
public class CpuAnalyzer implements JfrAnalyzer<CpuAnalysisResult> {

    private final int topN;

    public CpuAnalyzer() {
        this(10);
    }

    public CpuAnalyzer(int topN) {
        this.topN = topN > 0 ? topN : 10;
    }

    @Override
    public String getName() {
        return "CpuAnalyzer";
    }

    @Override
    public CpuAnalysisResult analyze(JfrRecordingReader reader) {
        String recordingName = reader.getRecordingPath().getFileName().toString();

        Map<String, AtomicLong> methodSamples = new HashMap<>();
        Map<String, Boolean> methodNativeMap = new HashMap<>();
        Map<String, List<String>> methodStackTraces = new HashMap<>();

        Map<String, AtomicLong> packageSamples = new HashMap<>();
        Map<String, AtomicLong> classSamples = new HashMap<>();
        Map<String, AtomicLong> threadSamples = new HashMap<>();

        List<Double> jvmUserCpuSamples = new ArrayList<>();
        List<Double> machineCpuSamples = new ArrayList<>();

        JfrEventFilter filter = JfrEventFilter.ofTypes(
            "jdk.ExecutionSample",
            "jdk.NativeMethodSample",
            "jdk.CPULoad"
        );

        long totalSampleCount = 0;

        try {
            reader.forEachEvent(filter, event -> {
                String typeName = event.getEventType().getName();

                if ("jdk.CPULoad".equals(typeName)) {
                    if (event.hasField("jvmUser")) {
                        jvmUserCpuSamples.add(event.getDouble("jvmUser"));
                    }
                    if (event.hasField("machineTotal")) {
                        machineCpuSamples.add(event.getDouble("machineTotal"));
                    }
                    return;
                }

                boolean isNative = "jdk.NativeMethodSample".equals(typeName);
                RecordedStackTrace stackTrace = event.getStackTrace();
                String threadName = JfrEventUtils.getThreadName(event);
                threadSamples.computeIfAbsent(threadName, k -> new AtomicLong()).incrementAndGet();

                if (stackTrace != null && !stackTrace.getFrames().isEmpty()) {
                    RecordedFrame topFrame = stackTrace.getFrames().get(0);
                    RecordedMethod method = topFrame.getMethod();
                    if (method != null) {
                        String methodStr = JfrEventUtils.formatMethod(method);
                        methodSamples.computeIfAbsent(methodStr, k -> new AtomicLong()).incrementAndGet();
                        methodNativeMap.putIfAbsent(methodStr, isNative);

                        if (!methodStackTraces.containsKey(methodStr)) {
                            methodStackTraces.put(methodStr, JfrEventUtils.extractStackTrace(event, 8));
                        }

                        RecordedClass clazz = method.getType();
                        if (clazz != null && clazz.getName() != null) {
                            String className = clazz.getName();
                            classSamples.computeIfAbsent(className, k -> new AtomicLong()).incrementAndGet();

                            int lastDot = className.lastIndexOf('.');
                            String pkg = lastDot > 0 ? className.substring(0, lastDot) : "(default)";
                            packageSamples.computeIfAbsent(pkg, k -> new AtomicLong()).incrementAndGet();
                        }
                    }
                }
            });
        } catch (Exception e) {
            // Keep what was processed
        }

        for (AtomicLong count : methodSamples.values()) {
            totalSampleCount += count.get();
        }

        final long total = totalSampleCount;

        List<CpuAnalysisResult.MethodSample> topMethods = methodSamples.entrySet().stream()
            .sorted((a, b) -> Long.compare(b.getValue().get(), a.getValue().get()))
            .limit(topN)
            .map(e -> {
                double pct = total > 0 ? (e.getValue().get() * 100.0) / total : 0.0;
                return new CpuAnalysisResult.MethodSample(
                    e.getKey(),
                    e.getValue().get(),
                    Math.round(pct * 100.0) / 100.0,
                    methodNativeMap.getOrDefault(e.getKey(), false),
                    methodStackTraces.getOrDefault(e.getKey(), Collections.emptyList())
                );
            })
            .collect(Collectors.toList());

        List<CpuAnalysisResult.PackageSample> topPackages = packageSamples.entrySet().stream()
            .sorted((a, b) -> Long.compare(b.getValue().get(), a.getValue().get()))
            .limit(topN)
            .map(e -> {
                double pct = total > 0 ? (e.getValue().get() * 100.0) / total : 0.0;
                return new CpuAnalysisResult.PackageSample(
                    e.getKey(),
                    e.getValue().get(),
                    Math.round(pct * 100.0) / 100.0
                );
            })
            .collect(Collectors.toList());

        List<CpuAnalysisResult.ClassSample> topClasses = classSamples.entrySet().stream()
            .sorted((a, b) -> Long.compare(b.getValue().get(), a.getValue().get()))
            .limit(topN)
            .map(e -> {
                double pct = total > 0 ? (e.getValue().get() * 100.0) / total : 0.0;
                return new CpuAnalysisResult.ClassSample(
                    e.getKey(),
                    e.getValue().get(),
                    Math.round(pct * 100.0) / 100.0
                );
            })
            .collect(Collectors.toList());

        List<CpuAnalysisResult.ThreadCpuSample> topThreads = threadSamples.entrySet().stream()
            .sorted((a, b) -> Long.compare(b.getValue().get(), a.getValue().get()))
            .limit(topN)
            .map(e -> {
                double pct = total > 0 ? (e.getValue().get() * 100.0) / total : 0.0;
                return new CpuAnalysisResult.ThreadCpuSample(
                    e.getKey(),
                    e.getValue().get(),
                    Math.round(pct * 100.0) / 100.0
                );
            })
            .collect(Collectors.toList());

        Double avgJvmUserCpu = jvmUserCpuSamples.isEmpty() ? null :
            jvmUserCpuSamples.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        Double avgMachineCpu = machineCpuSamples.isEmpty() ? null :
            machineCpuSamples.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

        List<PerformanceFinding> findings = new ArrayList<>();

        // Formulate findings based on measured evidence
        if (!topMethods.isEmpty() && total > 50) {
            CpuAnalysisResult.MethodSample topMethod = topMethods.get(0);
            if (topMethod.percentage() >= 30.0) {
                Evidence ev = Evidence.builder()
                    .metric("methodCpuSamplePercentage")
                    .value(topMethod.percentage())
                    .unit("percent_samples")
                    .source("jdk.ExecutionSample")
                    .confidence(0.90)
                    .description("Method " + topMethod.method() + " accounted for " + topMethod.percentage() + "% of execution samples (" + topMethod.samples() + "/" + total + " samples)")
                    .build();

                findings.add(PerformanceFinding.builder()
                    .category(FindingCategory.CPU)
                    .severity(topMethod.percentage() >= 50.0 ? FindingSeverity.HIGH : FindingSeverity.MEDIUM)
                    .confidence(0.88)
                    .title("Dominant CPU Hotspot Detected in " + topMethod.method())
                    .summary("Method " + topMethod.method() + " represents " + topMethod.percentage() + "% of all execution samples.")
                    .evidence(List.of(ev))
                    .impact("Significant CPU cycles concentrated in a single method; could indicate inefficient algorithmic loop, repeated parsing, or unoptimized data structure.")
                    .recommendedInvestigation(List.of(
                        "Review algorithm and loop structures in " + topMethod.method(),
                        "Check for redundant computation or missing caching in " + topMethod.method(),
                        "Inspect stack traces to identify calling callers leading to this hotspot"
                    ))
                    .build());
            }
        }

        if (avgMachineCpu != null && avgMachineCpu >= 0.85) {
            Evidence ev = Evidence.builder()
                .metric("averageMachineCpuLoad")
                .value(Math.round(avgMachineCpu * 100.0) / 100.0)
                .unit("ratio")
                .source("jdk.CPULoad")
                .confidence(0.95)
                .description("Machine CPU load averaged " + (Math.round(avgMachineCpu * 1000.0) / 10.0) + "%")
                .build();

            findings.add(PerformanceFinding.builder()
                .category(FindingCategory.CPU)
                .severity(FindingSeverity.HIGH)
                .confidence(0.92)
                .title("High Machine CPU Saturation")
                .summary("Host machine CPU utilization was elevated throughout the recording.")
                .evidence(List.of(ev))
                .impact("Elevated CPU utilization may cause thread scheduling starvation and increased response latency.")
                .recommendedInvestigation(List.of(
                    "Determine whether JVM user threads or external processes are consuming CPU",
                    "Check top CPU consuming threads in the recording"
                ))
                .build());
        }

        String note = "Percentages represent statistical share of captured execution/native samples (total: " +
            total + "), NOT guaranteed continuous CPU time. Distinguish statistical sample evidence from operating system CPU time.";

        return new CpuAnalysisResult(
            recordingName,
            total,
            topMethods,
            topPackages,
            topClasses,
            topThreads,
            avgJvmUserCpu != null ? Math.round(avgJvmUserCpu * 1000.0) / 1000.0 : null,
            avgMachineCpu != null ? Math.round(avgMachineCpu * 1000.0) / 1000.0 : null,
            findings,
            note
        );
    }
}
