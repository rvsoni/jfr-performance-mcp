package com.ravi.jfrmcp.jfr.analyzer;

import com.ravi.jfrmcp.jfr.JfrEventFilter;
import com.ravi.jfrmcp.jfr.JfrEventUtils;
import com.ravi.jfrmcp.jfr.JfrRecordingReader;
import com.ravi.jfrmcp.model.Evidence;
import com.ravi.jfrmcp.model.FindingCategory;
import com.ravi.jfrmcp.model.FindingSeverity;
import com.ravi.jfrmcp.model.GcAnalysisResult;
import com.ravi.jfrmcp.model.PerformanceFinding;
import jdk.jfr.consumer.RecordedEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Analyzes Garbage Collection events, pause times, and heap occupancy dynamics.
 */
public class GarbageCollectionAnalyzer implements JfrAnalyzer<GcAnalysisResult> {

    private static final String ASSESSMENT_RULES_DOC =
        "GC Assessment Rules: " +
        "CRITICAL: Max pause > 1000ms, Old/Full GC > 10, or post-GC heap occupancy > 90%. " +
        "HIGH: Max pause > 500ms, Old/Full GC > 3, post-GC heap occupancy > 80%, or total pause > 5000ms. " +
        "MEDIUM: Max pause > 200ms, post-GC heap occupancy > 70%, or total pause > 1500ms. " +
        "LOW: Normal garbage collection behavior within typical latency thresholds.";

    @Override
    public String getName() {
        return "GarbageCollectionAnalyzer";
    }

    @Override
    public GcAnalysisResult analyze(JfrRecordingReader reader) {
        String recordingName = reader.getRecordingPath().getFileName().toString();

        List<GcAnalysisResult.GcPauseEvent> pauses = new ArrayList<>();
        int youngGcCount = 0;
        int oldGcCount = 0;
        double totalPauseMs = 0.0;
        double maxPauseMs = 0.0;

        Long lastHeapUsedBefore = null;
        Long lastHeapUsedAfter = null;
        Long maxHeapSize = null;

        JfrEventFilter filter = JfrEventFilter.ofTypes(
            "jdk.GarbageCollection",
            "jdk.OldGarbageCollection",
            "jdk.GCPhasePause",
            "jdk.GCHeapSummary"
        );

        List<RecordedEvent> gcEvents = new ArrayList<>();
        reader.forEachEvent(filter, gcEvents::add);

        for (RecordedEvent event : gcEvents) {
            String typeName = event.getEventType().getName();

            if ("jdk.GarbageCollection".equals(typeName)) {
                String name = JfrEventUtils.getString(event, "name", "GC");
                String cause = JfrEventUtils.getString(event, "cause", "Unknown");
                double durationMs = JfrEventUtils.getDurationMs(event);

                totalPauseMs += durationMs;
                if (durationMs > maxPauseMs) {
                    maxPauseMs = durationMs;
                }

                if (name.toLowerCase().contains("old") || name.toLowerCase().contains("full")) {
                    oldGcCount++;
                } else {
                    youngGcCount++;
                }

                pauses.add(new GcAnalysisResult.GcPauseEvent(
                    name,
                    cause,
                    Math.round(durationMs * 100.0) / 100.0,
                    event.getStartTime().toString()
                ));
            } else if ("jdk.OldGarbageCollection".equals(typeName)) {
                oldGcCount++;
            } else if ("jdk.GCHeapSummary".equals(typeName)) {
                String when = JfrEventUtils.getString(event, "when", "");
                if (event.hasField("heapUsed")) {
                    long used = event.getLong("heapUsed");
                    if ("Before GC".equalsIgnoreCase(when)) {
                        lastHeapUsedBefore = used;
                    } else if ("After GC".equalsIgnoreCase(when)) {
                        lastHeapUsedAfter = used;
                    }
                }
                if (event.hasField("heapSpace")) {
                    try {
                        Object hs = event.getValue("heapSpace");
                        if (hs instanceof jdk.jfr.consumer.RecordedObject ro && ro.hasField("committedSize")) {
                            maxHeapSize = ro.getLong("committedSize");
                        }
                    } catch (Exception ignored) {}
                }
            }
        }

        int gcCount = youngGcCount + oldGcCount;
        double avgPauseMs = gcCount > 0 ? totalPauseMs / gcCount : 0.0;

        Double heapOccupancyPct = null;
        if (lastHeapUsedAfter != null && maxHeapSize != null && maxHeapSize > 0) {
            heapOccupancyPct = Math.round((lastHeapUsedAfter * 1000.0) / maxHeapSize) / 10.0;
        }

        // Apply explicit assessment rules
        String assessment = "LOW";
        FindingSeverity severity = FindingSeverity.INFO;

        if (maxPauseMs > 1000.0 || oldGcCount > 10 || (heapOccupancyPct != null && heapOccupancyPct > 90.0)) {
            assessment = "CRITICAL";
            severity = FindingSeverity.CRITICAL;
        } else if (maxPauseMs > 500.0 || oldGcCount > 3 || (heapOccupancyPct != null && heapOccupancyPct > 80.0) || totalPauseMs > 5000.0) {
            assessment = "HIGH";
            severity = FindingSeverity.HIGH;
        } else if (maxPauseMs > 200.0 || (heapOccupancyPct != null && heapOccupancyPct > 70.0) || totalPauseMs > 1500.0) {
            assessment = "MEDIUM";
            severity = FindingSeverity.MEDIUM;
        }

        List<PerformanceFinding> findings = new ArrayList<>();

        if (severity == FindingSeverity.HIGH || severity == FindingSeverity.CRITICAL) {
            List<Evidence> evidenceList = new ArrayList<>();
            evidenceList.add(Evidence.builder()
                .metric("maxGcPauseMs")
                .value(Math.round(maxPauseMs * 100.0) / 100.0)
                .unit("ms")
                .source("jdk.GarbageCollection")
                .confidence(0.98)
                .description("Maximum GC stop-the-world pause recorded")
                .build());

            if (oldGcCount > 0) {
                evidenceList.add(Evidence.builder()
                    .metric("oldGcCount")
                    .value(oldGcCount)
                    .unit("collections")
                    .source("jdk.GarbageCollection")
                    .confidence(0.99)
                    .description("Count of Full / Old generation garbage collections")
                    .build());
            }

            if (heapOccupancyPct != null) {
                evidenceList.add(Evidence.builder()
                    .metric("postGcHeapOccupancy")
                    .value(heapOccupancyPct)
                    .unit("percent")
                    .source("jdk.GCHeapSummary")
                    .confidence(0.95)
                    .description("Heap occupancy remaining immediately after GC")
                    .build());
            }

            findings.add(PerformanceFinding.builder()
                .category(FindingCategory.GC)
                .severity(severity)
                .confidence(0.95)
                .title("Elevated Garbage Collection Pause / Memory Pressure Detected")
                .summary("GC latency or heap occupancy exceeds operational thresholds (assessment: " + assessment + ").")
                .evidence(evidenceList)
                .impact("Long stop-the-world pauses cause direct application thread stalls and elevated p99 latency.")
                .recommendedInvestigation(List.of(
                    "Inspect allocation hotspots to reduce memory pressure",
                    "Check if heap sizing (-Xmx) is adequate for peak working set",
                    "Evaluate low-pause garbage collectors such as ZGC or Shenandoah if latency-sensitive"
                ))
                .build());
        }

        List<GcAnalysisResult.GcPauseEvent> longestPauses = pauses.stream()
            .sorted(Comparator.comparingDouble(GcAnalysisResult.GcPauseEvent::durationMs).reversed())
            .limit(10)
            .collect(Collectors.toList());

        return new GcAnalysisResult(
            recordingName,
            gcCount,
            Math.round(totalPauseMs * 100.0) / 100.0,
            Math.round(maxPauseMs * 100.0) / 100.0,
            Math.round(avgPauseMs * 100.0) / 100.0,
            youngGcCount,
            oldGcCount,
            lastHeapUsedBefore,
            lastHeapUsedAfter,
            maxHeapSize,
            heapOccupancyPct,
            assessment,
            ASSESSMENT_RULES_DOC,
            longestPauses,
            findings
        );
    }
}
