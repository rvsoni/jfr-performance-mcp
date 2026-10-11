package com.ravi.jfrmcp.jfr.analyzer;

import com.ravi.jfrmcp.jfr.JfrEventFilter;
import com.ravi.jfrmcp.jfr.JfrEventUtils;
import com.ravi.jfrmcp.jfr.JfrRecordingReader;
import com.ravi.jfrmcp.model.Evidence;
import com.ravi.jfrmcp.model.FindingCategory;
import com.ravi.jfrmcp.model.FindingSeverity;
import com.ravi.jfrmcp.model.IoAnalysisResult;
import com.ravi.jfrmcp.model.PerformanceFinding;
import jdk.jfr.consumer.RecordedEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Analyzes File and Socket I/O operations and latency from JFR events.
 */
public class IoAnalyzer implements JfrAnalyzer<IoAnalysisResult> {

    private final int topN;

    public IoAnalyzer() {
        this(10);
    }

    public IoAnalyzer(int topN) {
        this.topN = topN > 0 ? topN : 10;
    }

    @Override
    public String getName() {
        return "IoAnalyzer";
    }

    private static class IoAccumulator {
        long count = 0;
        long totalBytes = 0;
        double totalDurationMs = 0;
        double maxDurationMs = 0;

        void record(long bytes, double durationMs) {
            count++;
            totalBytes += bytes;
            totalDurationMs += durationMs;
            if (durationMs > maxDurationMs) {
                maxDurationMs = durationMs;
            }
        }

        IoAnalysisResult.IoCategorySummary toSummary() {
            double avg = count > 0 ? totalDurationMs / count : 0.0;
            return new IoAnalysisResult.IoCategorySummary(
                count,
                totalBytes,
                Math.round(totalDurationMs * 100.0) / 100.0,
                Math.round(maxDurationMs * 100.0) / 100.0,
                Math.round(avg * 100.0) / 100.0
            );
        }
    }

    @Override
    public IoAnalysisResult analyze(JfrRecordingReader reader) {
        String recordingName = reader.getRecordingPath().getFileName().toString();

        IoAccumulator fileReads = new IoAccumulator();
        IoAccumulator fileWrites = new IoAccumulator();
        IoAccumulator socketReads = new IoAccumulator();
        IoAccumulator socketWrites = new IoAccumulator();

        List<IoAnalysisResult.SlowIoEvent> slowEvents = new ArrayList<>();

        JfrEventFilter filter = JfrEventFilter.ofTypes(
            "jdk.FileRead",
            "jdk.FileWrite",
            "jdk.SocketRead",
            "jdk.SocketWrite"
        );

        List<RecordedEvent> events = new ArrayList<>();
        reader.forEachEvent(filter, events::add);

        for (RecordedEvent event : events) {
            String typeName = event.getEventType().getName();
            double durationMs = JfrEventUtils.getDurationMs(event);
            long bytes = JfrEventUtils.getLong(event, "bytesRead", 0);
            if (bytes == 0) {
                bytes = JfrEventUtils.getLong(event, "bytesWritten", 0);
            }

            String target = "";
            if (event.hasField("path")) {
                target = JfrEventUtils.getString(event, "path", "");
            } else if (event.hasField("host")) {
                String host = JfrEventUtils.getString(event, "host", "");
                int port = event.hasField("port") ? event.getInt("port") : 0;
                target = host + (port > 0 ? ":" + port : "");
            } else if (event.hasField("address")) {
                String addr = JfrEventUtils.getString(event, "address", "");
                int port = event.hasField("port") ? event.getInt("port") : 0;
                target = addr + (port > 0 ? ":" + port : "");
            }

            String threadName = JfrEventUtils.getThreadName(event);

            switch (typeName) {
                case "jdk.FileRead" -> fileReads.record(bytes, durationMs);
                case "jdk.FileWrite" -> fileWrites.record(bytes, durationMs);
                case "jdk.SocketRead" -> socketReads.record(bytes, durationMs);
                case "jdk.SocketWrite" -> socketWrites.record(bytes, durationMs);
            }

            // Capture slow I/O (e.g. > 100ms)
            if (durationMs >= 100.0) {
                slowEvents.add(new IoAnalysisResult.SlowIoEvent(
                    typeName,
                    target,
                    bytes,
                    Math.round(durationMs * 100.0) / 100.0,
                    threadName,
                    event.getStartTime().toString(),
                    JfrEventUtils.extractStackTrace(event, 6)
                ));
            }
        }

        List<IoAnalysisResult.SlowIoEvent> topSlowEvents = slowEvents.stream()
            .sorted(Comparator.comparingDouble(IoAnalysisResult.SlowIoEvent::durationMs).reversed())
            .limit(topN)
            .collect(Collectors.toList());

        List<PerformanceFinding> findings = new ArrayList<>();

        if (!topSlowEvents.isEmpty()) {
            IoAnalysisResult.SlowIoEvent slowest = topSlowEvents.get(0);
            if (slowest.durationMs() > 1000.0) {
                findings.add(PerformanceFinding.builder()
                    .category(FindingCategory.IO)
                    .severity(slowest.durationMs() > 5000.0 ? FindingSeverity.HIGH : FindingSeverity.MEDIUM)
                    .confidence(0.92)
                    .title("High I/O Latency Encountered (" + slowest.durationMs() + "ms on " + slowest.ioType() + ")")
                    .summary("Individual I/O call to target '" + slowest.target() + "' blocked thread for " + (slowest.durationMs() / 1000.0) + "s.")
                    .evidence(List.of(Evidence.builder()
                        .metric("maxIoDurationMs")
                        .value(slowest.durationMs())
                        .unit("ms")
                        .source(slowest.ioType())
                        .confidence(0.98)
                        .description("Slowest I/O event recorded on target " + slowest.target())
                        .build()))
                    .impact("Synchronous I/O blocks calling worker threads, delaying requests and causing thread pool starvation.")
                    .recommendedInvestigation(List.of(
                        "Inspect downstream network service response time or disk write throughput for " + slowest.target(),
                        "Evaluate socket timeout configurations (SO_TIMEOUT / connect timeout)",
                        "Consider asynchronous or non-blocking I/O (NIO) for high-latency external dependencies"
                    ))
                    .build());
            }
        }

        return new IoAnalysisResult(
            recordingName,
            fileReads.toSummary(),
            fileWrites.toSummary(),
            socketReads.toSummary(),
            socketWrites.toSummary(),
            topSlowEvents,
            findings
        );
    }
}
