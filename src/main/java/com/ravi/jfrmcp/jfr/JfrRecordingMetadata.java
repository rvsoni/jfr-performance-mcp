package com.ravi.jfrmcp.jfr;

import com.ravi.jfrmcp.model.JvmInfo;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Metadata extracted from a JFR recording header, descriptor, and environment events.
 */
public record JfrRecordingMetadata(
    Path filePath,
    long fileSizeBytes,
    Instant startTime,
    Instant endTime,
    Double durationSeconds,
    JvmInfo jvmInfo,
    long totalEvents,
    int eventTypeCount,
    List<String> eventTypeNames,
    Map<String, Object> attributes
) {
    public JfrRecordingMetadata {
        eventTypeNames = eventTypeNames != null ? List.copyOf(eventTypeNames) : Collections.emptyList();
        attributes = attributes != null ? Map.copyOf(attributes) : Collections.emptyMap();
    }
}
