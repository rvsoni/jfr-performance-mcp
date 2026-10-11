package com.ravi.jfrmcp.jfr.analyzer;

import com.ravi.jfrmcp.jfr.JfrRecordingMetadata;
import com.ravi.jfrmcp.jfr.JfrRecordingReader;
import com.ravi.jfrmcp.model.RecordingSummary;

/**
 * Analyzes JFR recording headers and environment events to produce a RecordingSummary.
 */
public class RecordingSummaryAnalyzer implements JfrAnalyzer<RecordingSummary> {

    @Override
    public String getName() {
        return "RecordingSummaryAnalyzer";
    }

    @Override
    public RecordingSummary analyze(JfrRecordingReader reader) {
        JfrRecordingMetadata meta = reader.readMetadata();

        return RecordingSummary.builder()
            .fileName(meta.filePath().getFileName().toString())
            .filePath(meta.filePath().toAbsolutePath().normalize().toString())
            .fileSizeBytes(meta.fileSizeBytes())
            .startTime(meta.startTime())
            .endTime(meta.endTime())
            .durationSeconds(meta.durationSeconds())
            .jvm(meta.jvmInfo())
            .eventCount(meta.totalEvents())
            .eventTypeCount(meta.eventTypeCount())
            .eventTypes(meta.eventTypeNames())
            .metadata(meta.attributes())
            .build();
    }
}
