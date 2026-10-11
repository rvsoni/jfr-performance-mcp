package com.ravi.jfrmcp.jfr.analyzer;

import com.ravi.jfrmcp.jfr.JfrEventFilter;
import com.ravi.jfrmcp.jfr.JfrEventUtils;
import com.ravi.jfrmcp.jfr.JfrRecordingReader;
import com.ravi.jfrmcp.model.Evidence;
import com.ravi.jfrmcp.model.FindingCategory;
import com.ravi.jfrmcp.model.FindingSeverity;
import com.ravi.jfrmcp.model.LockAnalysisResult;
import com.ravi.jfrmcp.model.PerformanceFinding;
import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Analyzes Java monitor enter synchronization events to detect lock contention hotspots and thread serialization bottlenecks.
 */
public class LockAnalyzer implements JfrAnalyzer<LockAnalysisResult> {

    private final int topN;

    public LockAnalyzer() {
        this(10);
    }

    public LockAnalyzer(int topN) {
        this.topN = topN > 0 ? topN : 10;
    }

    @Override
    public String getName() {
        return "LockAnalyzer";
    }

    private static class MonitorAggregator {
        final String monitorClass;
        long eventCount = 0;
        double totalBlockedTimeMs = 0;
        double maxBlockedTimeMs = 0;
        final Set<String> contendingThreads = new HashSet<>();
        List<String> sampleStackTrace = Collections.emptyList();

        MonitorAggregator(String monitorClass) {
            this.monitorClass = monitorClass;
        }
    }

    private static class LocationAggregator {
        final String location;
        long eventCount = 0;
        double totalDurationMs = 0;
        List<String> sampleStackTrace = Collections.emptyList();

        LocationAggregator(String location) {
            this.location = location;
        }
    }

    @Override
    public LockAnalysisResult analyze(JfrRecordingReader reader) {
        String recordingName = reader.getRecordingPath().getFileName().toString();

        Map<String, MonitorAggregator> monitorMap = new HashMap<>();
        Map<String, LocationAggregator> locationMap = new HashMap<>();

        long totalEvents = 0;
        double grandTotalDurationMs = 0.0;

        JfrEventFilter filter = JfrEventFilter.ofTypes("jdk.JavaMonitorEnter");

        List<RecordedEvent> events = new ArrayList<>();
        reader.forEachEvent(filter, events::add);

        for (RecordedEvent event : events) {
            totalEvents++;
            double durationMs = JfrEventUtils.getDurationMs(event);
            grandTotalDurationMs += durationMs;

            RecordedClass monitorClassObj = null;
            if (event.hasField("monitorClass")) {
                try {
                    monitorClassObj = event.getClass("monitorClass");
                } catch (Exception ignored) {}
            }

            String className = monitorClassObj != null && monitorClassObj.getName() != null
                ? monitorClassObj.getName()
                : "UnknownMonitor";

            String threadName = JfrEventUtils.getThreadName(event);

            MonitorAggregator agg = monitorMap.computeIfAbsent(className, MonitorAggregator::new);
            agg.eventCount++;
            agg.totalBlockedTimeMs += durationMs;
            if (durationMs > agg.maxBlockedTimeMs) {
                agg.maxBlockedTimeMs = durationMs;
            }
            agg.contendingThreads.add(threadName);
            if (agg.sampleStackTrace.isEmpty()) {
                agg.sampleStackTrace = JfrEventUtils.extractStackTrace(event, 8);
            }

            // Top lock location (from top frame or address)
            List<String> frames = JfrEventUtils.extractStackTrace(event, 1);
            String location = frames.isEmpty() ? className : frames.get(0);
            LocationAggregator locAgg = locationMap.computeIfAbsent(location, LocationAggregator::new);
            locAgg.eventCount++;
            locAgg.totalDurationMs += durationMs;
            if (locAgg.sampleStackTrace.isEmpty()) {
                locAgg.sampleStackTrace = JfrEventUtils.extractStackTrace(event, 6);
            }
        }

        List<LockAnalysisResult.LockContentionSummary> contentions = monitorMap.values().stream()
            .sorted(Comparator.comparingDouble((MonitorAggregator a) -> a.totalBlockedTimeMs).reversed())
            .limit(topN)
            .map(agg -> new LockAnalysisResult.LockContentionSummary(
                agg.monitorClass,
                agg.contendingThreads.size(),
                agg.eventCount,
                Math.round(agg.totalBlockedTimeMs * 100.0) / 100.0,
                Math.round(agg.maxBlockedTimeMs * 100.0) / 100.0,
                agg.eventCount > 0 ? Math.round((agg.totalBlockedTimeMs / agg.eventCount) * 100.0) / 100.0 : 0.0,
                agg.contendingThreads.stream().limit(5).collect(Collectors.toList()),
                agg.sampleStackTrace
            ))
            .collect(Collectors.toList());

        List<LockAnalysisResult.ContendedLocation> topLocations = locationMap.values().stream()
            .sorted(Comparator.comparingDouble((LocationAggregator a) -> a.totalDurationMs).reversed())
            .limit(topN)
            .map(loc -> new LockAnalysisResult.ContendedLocation(
                loc.location,
                loc.eventCount,
                Math.round(loc.totalDurationMs * 100.0) / 100.0,
                loc.sampleStackTrace
            ))
            .collect(Collectors.toList());

        List<PerformanceFinding> findings = new ArrayList<>();

        if (!contentions.isEmpty()) {
            LockAnalysisResult.LockContentionSummary topContention = contentions.get(0);
            if (topContention.blockedTimeMs() > 1000.0 || topContention.blockedThreads() > 5) {
                FindingSeverity severity = topContention.blockedTimeMs() > 10000.0 || topContention.blockedThreads() > 20
                    ? FindingSeverity.HIGH
                    : FindingSeverity.MEDIUM;

                List<Evidence> evidenceList = List.of(
                    Evidence.builder()
                        .metric("blockedThreadsCount")
                        .value(topContention.blockedThreads())
                        .unit("threads")
                        .source("jdk.JavaMonitorEnter")
                        .confidence(0.95)
                        .description("Threads blocked on monitor " + topContention.monitorClass())
                        .build(),
                    Evidence.builder()
                        .metric("cumulativeBlockedTimeMs")
                        .value(topContention.blockedTimeMs())
                        .unit("ms")
                        .source("jdk.JavaMonitorEnter")
                        .confidence(0.98)
                        .description("Total cumulative wait time spent waiting for " + topContention.monitorClass())
                        .build()
                );

                findings.add(PerformanceFinding.builder()
                    .category(FindingCategory.LOCK_CONTENTION)
                    .severity(severity)
                    .confidence(0.92)
                    .title("Severe Lock Contention on " + topContention.monitorClass())
                    .summary(topContention.blockedThreads() + " threads experienced " +
                        (Math.round(topContention.blockedTimeMs() / 10.0) / 100.0) +
                        "s cumulative blocked time contending for " + topContention.monitorClass() + ".")
                    .evidence(evidenceList)
                    .impact("Thread execution is serialized, causing latency spikes and underutilization of available CPU cores.")
                    .recommendedInvestigation(List.of(
                        "Inspect synchronized blocks or methods associated with " + topContention.monitorClass(),
                        "Check if fine-grained locking (e.g. ConcurrentHashMap, ReadWriteLock, StampedLock) can replace coarse synchronization",
                        "Examine stack traces of contending threads to see critical section duration"
                    ))
                    .build());
            }
        }

        return new LockAnalysisResult(
            recordingName,
            totalEvents,
            Math.round(grandTotalDurationMs * 100.0) / 100.0,
            contentions,
            topLocations,
            findings
        );
    }
}
