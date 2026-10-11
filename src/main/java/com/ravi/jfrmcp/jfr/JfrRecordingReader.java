package com.ravi.jfrmcp.jfr;

import com.ravi.jfrmcp.exception.InvalidRecordingException;
import com.ravi.jfrmcp.exception.RecordingNotFoundException;
import com.ravi.jfrmcp.model.JvmInfo;
import jdk.jfr.EventType;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Safely reads and streams events from a JFR recording file using jdk.jfr.consumer.RecordingFile.
 * Designed to process large recordings efficiently without loading all events into memory.
 */
public class JfrRecordingReader {

    private static final Logger log = LoggerFactory.getLogger(JfrRecordingReader.class);

    private final Path recordingPath;

    public JfrRecordingReader(Path recordingPath) {
        this.recordingPath = Objects.requireNonNull(recordingPath, "recordingPath cannot be null");
        validateFile();
    }

    private void validateFile() {
        if (!Files.exists(recordingPath)) {
            throw new RecordingNotFoundException("Recording file does not exist: " + recordingPath);
        }
        if (Files.isDirectory(recordingPath)) {
            throw new InvalidRecordingException("Path is a directory, not a recording file: " + recordingPath);
        }
        String name = recordingPath.getFileName().toString().toLowerCase();
        if (!name.endsWith(".jfr")) {
            throw new InvalidRecordingException("File is not a JFR recording (missing .jfr extension): " + recordingPath);
        }
    }

    /**
     * Streams events through the given consumer according to the filter predicate.
     * Events are consumed sequentially and garbage-collected, avoiding heap exhaustion.
     */
    public long forEachEvent(JfrEventFilter filter, Consumer<RecordedEvent> consumer) {
        Objects.requireNonNull(consumer, "consumer cannot be null");
        long count = 0;

        try (RecordingFile recordingFile = new RecordingFile(recordingPath)) {
            while (recordingFile.hasMoreEvents()) {
                RecordedEvent event;
                try {
                    event = recordingFile.readEvent();
                } catch (Exception e) {
                    log.warn("Corrupted or unreadable event in {}: {}", recordingPath.getFileName(), e.getMessage());
                    break;
                }

                if (event == null) {
                    continue;
                }

                if (filter == null || filter.test(event)) {
                    consumer.accept(event);
                    count++;
                }
            }
        } catch (IOException e) {
            log.error("Failed to read JFR recording: {}", recordingPath, e);
            throw new InvalidRecordingException("Failed to read JFR file '" + recordingPath.getFileName() + "': " + e.getMessage(), e);
        }

        return count;
    }

    /**
     * Reads event types and recording metadata.
     */
    public JfrRecordingMetadata readMetadata() {
        long fileSize;
        try {
            fileSize = Files.size(recordingPath);
        } catch (IOException e) {
            fileSize = -1L;
        }

        List<String> eventTypeNames = new ArrayList<>();
        try (RecordingFile recordingFile = new RecordingFile(recordingPath)) {
            List<EventType> eventTypes = recordingFile.readEventTypes();
            if (eventTypes != null) {
                for (EventType type : eventTypes) {
                    eventTypeNames.add(type.getName());
                }
            }
        } catch (IOException e) {
            log.error("Failed to read event types from recording: {}", recordingPath, e);
            throw new InvalidRecordingException("Unable to read event types from '" + recordingPath.getFileName() + "': " + e.getMessage(), e);
        }

        Collections.sort(eventTypeNames);

        // Scan environment events and timeframe
        Instant minStart = null;
        Instant maxEnd = null;
        long totalEvents = 0;

        String jvmVersion = null;
        String jvmVendor = null;
        String jvmRuntime = null;
        String vmName = null;
        String vmVersion = null;
        List<String> jvmArguments = new ArrayList<>();
        String os = null;
        Integer cpuCount = null;
        Long totalMemory = null;
        Long initialHeap = null;
        Long maxHeap = null;

        Map<String, Object> attributes = new HashMap<>();

        try (RecordingFile recordingFile = new RecordingFile(recordingPath)) {
            while (recordingFile.hasMoreEvents()) {
                RecordedEvent event;
                try {
                    event = recordingFile.readEvent();
                } catch (Exception e) {
                    break;
                }
                if (event == null) {
                    continue;
                }
                totalEvents++;

                Instant eventStart = event.getStartTime();
                if (minStart == null || eventStart.isBefore(minStart)) {
                    minStart = eventStart;
                }
                Instant eventEnd = event.getEndTime();
                if (maxEnd == null || eventEnd.isAfter(maxEnd)) {
                    maxEnd = eventEnd;
                }

                String typeName = event.getEventType().getName();
                if ("jdk.JVMInformation".equals(typeName)) {
                    jvmVersion = JfrEventUtils.getString(event, "jvmVersion", jvmVersion);
                    jvmVendor = JfrEventUtils.getString(event, "jvmVendor", jvmVendor);
                    jvmRuntime = JfrEventUtils.getString(event, "jvmName", jvmRuntime);
                    vmName = JfrEventUtils.getString(event, "jvmName", vmName);
                    String jvmArgsStr = JfrEventUtils.getString(event, "jvmArguments", null);
                    if (jvmArgsStr != null && !jvmArgsStr.isBlank()) {
                        jvmArguments = List.of(jvmArgsStr.split("\\s+"));
                    }
                    if (event.hasField("cpuCount")) {
                        cpuCount = JfrEventUtils.getInt(event, "cpuCount", 0);
                    }
                } else if ("jdk.OSInformation".equals(typeName)) {
                    os = JfrEventUtils.getString(event, "osVersion", os);
                } else if ("jdk.GCHeapConfiguration".equals(typeName)) {
                    if (event.hasField("initialSize")) {
                        initialHeap = event.getLong("initialSize");
                    }
                    if (event.hasField("maxSize")) {
                        maxHeap = event.getLong("maxSize");
                    }
                } else if ("jdk.PhysicalMemory".equals(typeName)) {
                    if (event.hasField("totalSize")) {
                        totalMemory = event.getLong("totalSize");
                    }
                }
            }
        } catch (IOException e) {
            log.warn("Encountered error while scanning recording timeframe: {}", e.getMessage());
        }

        Double durationSec = null;
        if (minStart != null && maxEnd != null) {
            durationSec = Duration.between(minStart, maxEnd).toNanos() / 1_000_000_000.0;
        }

        JvmInfo jvmInfo = JvmInfo.builder()
            .version(jvmVersion)
            .vendor(jvmVendor)
            .runtime(jvmRuntime)
            .vmName(vmName)
            .vmVersion(vmVersion)
            .jvmArguments(jvmArguments)
            .os(os)
            .cpuCount(cpuCount)
            .totalMemoryBytes(totalMemory)
            .initialHeapBytes(initialHeap)
            .maxHeapBytes(maxHeap)
            .build();

        return new JfrRecordingMetadata(
            recordingPath,
            fileSize,
            minStart,
            maxEnd,
            durationSec,
            jvmInfo,
            totalEvents,
            eventTypeNames.size(),
            eventTypeNames,
            attributes
        );
    }

    public Path getRecordingPath() {
        return recordingPath;
    }
}
