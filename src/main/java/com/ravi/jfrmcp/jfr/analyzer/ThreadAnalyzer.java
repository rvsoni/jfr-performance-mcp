package com.ravi.jfrmcp.jfr.analyzer;

import com.ravi.jfrmcp.jfr.JfrEventFilter;
import com.ravi.jfrmcp.jfr.JfrEventUtils;
import com.ravi.jfrmcp.jfr.JfrRecordingReader;
import com.ravi.jfrmcp.model.Evidence;
import com.ravi.jfrmcp.model.FindingCategory;
import com.ravi.jfrmcp.model.FindingSeverity;
import com.ravi.jfrmcp.model.PerformanceFinding;
import com.ravi.jfrmcp.model.ThreadAnalysisResult;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedThread;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Analyzes Java thread concurrency, thread counts, thread states, and CPU activity per thread.
 */
public class ThreadAnalyzer implements JfrAnalyzer<ThreadAnalysisResult> {

    @Override
    public String getName() {
        return "ThreadAnalyzer";
    }

    @Override
    public ThreadAnalysisResult analyze(JfrRecordingReader reader) {
        String recordingName = reader.getRecordingPath().getFileName().toString();

        Set<String> uniqueThreadNames = new HashSet<>();
        Set<String> blockedThreadNames = new HashSet<>();
        Set<String> waitingThreadNames = new HashSet<>();
        Set<String> parkedThreadNames = new HashSet<>();
        Map<String, Boolean> threadDaemonMap = new HashMap<>();
        Map<String, AtomicLong> threadCpuSamples = new HashMap<>();

        int peakThreadCount = 0;
        int activeThreadCount = 0;

        JfrEventFilter filter = JfrEventFilter.ofTypes(
            "jdk.JavaThreadStatistics",
            "jdk.ThreadStart",
            "jdk.ThreadEnd",
            "jdk.ThreadPark",
            "jdk.ThreadSleep",
            "jdk.JavaMonitorEnter",
            "jdk.JavaMonitorWait",
            "jdk.ExecutionSample"
        );

        List<RecordedEvent> events = new ArrayList<>();
        reader.forEachEvent(filter, events::add);

        for (RecordedEvent event : events) {
            String typeName = event.getEventType().getName();

            if ("jdk.JavaThreadStatistics".equals(typeName)) {
                if (event.hasField("peakCount")) {
                    int peak = JfrEventUtils.getInt(event, "peakCount", 0);
                    if (peak > peakThreadCount) {
                        peakThreadCount = peak;
                    }
                }
                if (event.hasField("activeCount")) {
                    activeThreadCount = JfrEventUtils.getInt(event, "activeCount", 0);
                }
                continue;
            }

            RecordedThread thread = event.getThread();
            String threadName = JfrEventUtils.getThreadName(event);
            if (!"unknown".equals(threadName)) {
                uniqueThreadNames.add(threadName);
            }

            if (thread != null && thread.getJavaName() != null) {
                // If daemon field is present
                if (thread.hasField("isDaemon")) {
                    threadDaemonMap.putIfAbsent(threadName, thread.getBoolean("isDaemon"));
                }
            }

            if ("jdk.JavaMonitorEnter".equals(typeName)) {
                blockedThreadNames.add(threadName);
            } else if ("jdk.JavaMonitorWait".equals(typeName) || "jdk.ThreadSleep".equals(typeName)) {
                waitingThreadNames.add(threadName);
            } else if ("jdk.ThreadPark".equals(typeName)) {
                parkedThreadNames.add(threadName);
            } else if ("jdk.ExecutionSample".equals(typeName)) {
                threadCpuSamples.computeIfAbsent(threadName, k -> new AtomicLong()).incrementAndGet();
            }
        }

        int totalThreads = Math.max(uniqueThreadNames.size(), activeThreadCount);
        if (peakThreadCount == 0) {
            peakThreadCount = totalThreads;
        }

        long totalCpuSamples = 0;
        for (AtomicLong cnt : threadCpuSamples.values()) {
            totalCpuSamples += cnt.get();
        }

        final long grandTotalCpu = totalCpuSamples;

        List<ThreadAnalysisResult.ThreadCpuActivity> topCpuThreads = threadCpuSamples.entrySet().stream()
            .sorted((a, b) -> Long.compare(b.getValue().get(), a.getValue().get()))
            .limit(10)
            .map(e -> {
                double pct = grandTotalCpu > 0 ? (e.getValue().get() * 100.0) / grandTotalCpu : 0.0;
                return new ThreadAnalysisResult.ThreadCpuActivity(
                    e.getKey(),
                    e.getValue().get(),
                    Math.round(pct * 100.0) / 100.0
                );
            })
            .collect(Collectors.toList());

        List<ThreadAnalysisResult.ThreadSummary> threadSummaries = uniqueThreadNames.stream()
            .limit(50)
            .map(tName -> {
                String state = "RUNNABLE";
                if (blockedThreadNames.contains(tName)) {
                    state = "BLOCKED";
                } else if (waitingThreadNames.contains(tName)) {
                    state = "TIMED_WAITING/WAITING";
                } else if (parkedThreadNames.contains(tName)) {
                    state = "PARKED";
                }
                long samples = threadCpuSamples.getOrDefault(tName, new AtomicLong(0)).get();
                boolean isDaemon = threadDaemonMap.getOrDefault(tName, false);
                return new ThreadAnalysisResult.ThreadSummary(tName, state, isDaemon, samples);
            })
            .collect(Collectors.toList());

        List<PerformanceFinding> findings = new ArrayList<>();

        if (blockedThreadNames.size() > 5) {
            findings.add(PerformanceFinding.builder()
                .category(FindingCategory.THREAD)
                .severity(blockedThreadNames.size() > 20 ? FindingSeverity.HIGH : FindingSeverity.MEDIUM)
                .confidence(0.92)
                .title("High Volume of Blocked Threads (" + blockedThreadNames.size() + " threads)")
                .summary(blockedThreadNames.size() + " distinct threads encountered synchronization lock blocking.")
                .evidence(List.of(Evidence.builder()
                    .metric("blockedThreadCount")
                    .value(blockedThreadNames.size())
                    .unit("threads")
                    .source("jdk.JavaMonitorEnter")
                    .confidence(0.95)
                    .description("Distinct threads encountering monitor enter contention")
                    .build()))
                .impact("Blocked threads cannot process incoming workload, increasing latency queues.")
                .recommendedInvestigation(List.of(
                    "Inspect lock contention analyzer for congested monitor instances",
                    "Check for thread pool saturation or synchronous cascade calls"
                ))
                .build());
        }

        if (totalThreads > 500) {
            findings.add(PerformanceFinding.builder()
                .category(FindingCategory.THREAD)
                .severity(FindingSeverity.MEDIUM)
                .confidence(0.85)
                .title("High Total Thread Count (" + totalThreads + " threads)")
                .summary("JVM has a large number of active/registered threads.")
                .evidence(List.of(Evidence.builder()
                    .metric("totalThreads")
                    .value(totalThreads)
                    .unit("threads")
                    .source("jdk.JavaThreadStatistics")
                    .confidence(0.90)
                    .description("Active thread count across recording")
                    .build()))
                .impact("High thread count consumes thread stack memory (typically 1MB per thread) and causes OS context switching overhead.")
                .recommendedInvestigation(List.of(
                    "Verify unbounded thread pools or executor leaks",
                    "Consider Java 21+ Virtual Threads (Project Loom) for high-concurrency I/O tasks"
                ))
                .build());
        }

        return new ThreadAnalysisResult(
            recordingName,
            totalThreads,
            activeThreadCount > 0 ? activeThreadCount : totalThreads,
            peakThreadCount,
            blockedThreadNames.size(),
            waitingThreadNames.size(),
            parkedThreadNames.size(),
            threadSummaries,
            topCpuThreads,
            findings
        );
    }
}
