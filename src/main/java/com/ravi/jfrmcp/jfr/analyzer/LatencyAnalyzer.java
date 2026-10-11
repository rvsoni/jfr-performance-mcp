package com.ravi.jfrmcp.jfr.analyzer;

import com.ravi.jfrmcp.jfr.JfrEventFilter;
import com.ravi.jfrmcp.jfr.JfrEventUtils;
import com.ravi.jfrmcp.jfr.JfrRecordingReader;
import com.ravi.jfrmcp.model.Evidence;
import com.ravi.jfrmcp.model.FindingCategory;
import com.ravi.jfrmcp.model.FindingSeverity;
import com.ravi.jfrmcp.model.LatencyAnalysisResult;
import com.ravi.jfrmcp.model.PerformanceFinding;
import jdk.jfr.consumer.RecordedEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Investigates latency outliers across JVM execution, pauses, monitor contention, thread parks, and I/O.
 */
public class LatencyAnalyzer implements JfrAnalyzer<LatencyAnalysisResult> {

    private static final String DISCLAIMER =
        "Note: Latency events measure JVM-level pauses, monitor waits, socket/file I/O, and thread parks. " +
        "JFR does not measure end-to-end application HTTP/RPC latency unless custom application events or OpenTelemetry spans were recorded.";

    private final double thresholdMs;

    public LatencyAnalyzer() {
        this(100.0);
    }

    public LatencyAnalyzer(double thresholdMs) {
        this.thresholdMs = thresholdMs > 0 ? thresholdMs : 100.0;
    }

    @Override
    public String getName() {
        return "LatencyAnalyzer";
    }

    @Override
    public LatencyAnalysisResult analyze(JfrRecordingReader reader) {
        String recordingName = reader.getRecordingPath().getFileName().toString();

        List<LatencyAnalysisResult.LongRunningEvent> longEvents = new ArrayList<>();

        JfrEventFilter filter = JfrEventFilter.ofTypes(
            "jdk.JavaMonitorEnter",
            "jdk.JavaMonitorWait",
            "jdk.ThreadPark",
            "jdk.ThreadSleep",
            "jdk.GarbageCollection",
            "jdk.GCPhasePause",
            "jdk.SocketRead",
            "jdk.SocketWrite",
            "jdk.FileRead",
            "jdk.FileWrite"
        );

        List<RecordedEvent> events = new ArrayList<>();
        reader.forEachEvent(filter, events::add);

        for (RecordedEvent event : events) {
            double durationMs = JfrEventUtils.getDurationMs(event);
            if (durationMs >= thresholdMs) {
                String typeName = event.getEventType().getName();
                String threadName = JfrEventUtils.getThreadName(event);

                String details = "";
                if (event.hasField("monitorClass")) {
                    try {
                        details = "Monitor: " + event.getClass("monitorClass").getName();
                    } catch (Exception ignored) {}
                } else if (event.hasField("path")) {
                    details = "Path: " + JfrEventUtils.getString(event, "path", "");
                } else if (event.hasField("host")) {
                    details = "Host: " + JfrEventUtils.getString(event, "host", "");
                } else if (event.hasField("name")) {
                    details = "Name: " + JfrEventUtils.getString(event, "name", "");
                }

                longEvents.add(new LatencyAnalysisResult.LongRunningEvent(
                    typeName,
                    Math.round(durationMs * 100.0) / 100.0,
                    event.getStartTime().toString(),
                    threadName,
                    details,
                    JfrEventUtils.extractStackTrace(event, 6)
                ));
            }
        }

        List<LatencyAnalysisResult.LongRunningEvent> topLongEvents = longEvents.stream()
            .sorted(Comparator.comparingDouble(LatencyAnalysisResult.LongRunningEvent::durationMs).reversed())
            .limit(20)
            .collect(Collectors.toList());

        List<PerformanceFinding> findings = new ArrayList<>();

        if (!topLongEvents.isEmpty()) {
            LatencyAnalysisResult.LongRunningEvent maxEvent = topLongEvents.get(0);
            if (maxEvent.durationMs() >= 1000.0) {
                findings.add(PerformanceFinding.builder()
                    .category(FindingCategory.LATENCY)
                    .severity(maxEvent.durationMs() >= 5000.0 ? FindingSeverity.HIGH : FindingSeverity.MEDIUM)
                    .confidence(0.92)
                    .title("Extended Latency Spike Detected (" + maxEvent.durationMs() + "ms in " + maxEvent.eventType() + ")")
                    .summary("A long pause event occurred in thread '" + maxEvent.threadName() + "' with duration " +
                        (maxEvent.durationMs() / 1000.0) + "s.")
                    .evidence(List.of(Evidence.builder()
                        .metric("maxLatencyDurationMs")
                        .value(maxEvent.durationMs())
                        .unit("ms")
                        .source(maxEvent.eventType())
                        .confidence(0.98)
                        .description("Peak pause duration in " + maxEvent.eventType() + " (" + maxEvent.details() + ")")
                        .build()))
                    .impact("Significant thread suspension causing downstream request timeout or degradation.")
                    .recommendedInvestigation(List.of(
                        "Review call stack of the longest running event",
                        "Correlate event timestamp with application logs and external latency alarms"
                    ))
                    .build());
            }
        }

        return new LatencyAnalysisResult(
            recordingName,
            thresholdMs,
            longEvents.size(),
            topLongEvents,
            findings,
            DISCLAIMER
        );
    }
}
