package com.ravi.jfrmcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Summary of a JFR recording including file metrics, timeframe, event counts, and JVM metadata.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RecordingSummary(
    String fileName,
    String filePath,
    long fileSizeBytes,
    Instant startTime,
    Instant endTime,
    Double durationSeconds,
    JvmInfo jvm,
    long eventCount,
    int eventTypeCount,
    List<String> eventTypes,
    Map<String, Object> metadata
) {
    public RecordingSummary {
        eventTypes = eventTypes != null ? List.copyOf(eventTypes) : Collections.emptyList();
        metadata = metadata != null ? Map.copyOf(metadata) : Collections.emptyMap();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String fileName;
        private String filePath;
        private long fileSizeBytes;
        private Instant startTime;
        private Instant endTime;
        private Double durationSeconds;
        private JvmInfo jvm;
        private long eventCount;
        private int eventTypeCount;
        private List<String> eventTypes = Collections.emptyList();
        private Map<String, Object> metadata = Collections.emptyMap();

        public Builder fileName(String fileName) {
            this.fileName = fileName;
            return this;
        }

        public Builder filePath(String filePath) {
            this.filePath = filePath;
            return this;
        }

        public Builder fileSizeBytes(long fileSizeBytes) {
            this.fileSizeBytes = fileSizeBytes;
            return this;
        }

        public Builder startTime(Instant startTime) {
            this.startTime = startTime;
            return this;
        }

        public Builder endTime(Instant endTime) {
            this.endTime = endTime;
            return this;
        }

        public Builder durationSeconds(Double durationSeconds) {
            this.durationSeconds = durationSeconds;
            return this;
        }

        public Builder jvm(JvmInfo jvm) {
            this.jvm = jvm;
            return this;
        }

        public Builder eventCount(long eventCount) {
            this.eventCount = eventCount;
            return this;
        }

        public Builder eventTypeCount(int eventTypeCount) {
            this.eventTypeCount = eventTypeCount;
            return this;
        }

        public Builder eventTypes(List<String> eventTypes) {
            this.eventTypes = eventTypes;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public RecordingSummary build() {
            return new RecordingSummary(fileName, filePath, fileSizeBytes, startTime, endTime, durationSeconds, jvm, eventCount, eventTypeCount, eventTypes, metadata);
        }
    }
}
