package com.ravi.jfrmcp.jfr;

import com.ravi.jfrmcp.TestRecordingFactory;
import com.ravi.jfrmcp.exception.InvalidRecordingException;
import com.ravi.jfrmcp.exception.RecordingNotFoundException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JfrRecordingReaderTest {

    private static Path realisticRecording;
    private static Path emptyRecording;

    @TempDir
    static Path tempDir;

    @BeforeAll
    static void setUpAll() throws Exception {
        realisticRecording = TestRecordingFactory.createRealisticRecording(tempDir.resolve("test-realistic.jfr"));
        emptyRecording = TestRecordingFactory.createEmptyRecording();
    }

    @Test
    void shouldReadMetadataFromRealisticRecording() {
        JfrRecordingReader reader = new JfrRecordingReader(realisticRecording);
        JfrRecordingMetadata metadata = reader.readMetadata();

        assertThat(metadata.filePath()).isEqualTo(realisticRecording);
        assertThat(metadata.fileSizeBytes()).isGreaterThan(0);
        assertThat(metadata.totalEvents()).isGreaterThan(0);
        assertThat(metadata.eventTypeCount()).isGreaterThan(0);
        assertThat(metadata.eventTypeNames()).contains("jdk.ExecutionSample", "jdk.JavaMonitorEnter");
    }

    @Test
    void shouldStreamFilteredEvents() {
        JfrRecordingReader reader = new JfrRecordingReader(realisticRecording);
        AtomicLong cpuSampleCount = new AtomicLong();

        long processed = reader.forEachEvent(
            JfrEventFilter.ofTypes("jdk.ExecutionSample"),
            event -> cpuSampleCount.incrementAndGet()
        );

        assertThat(processed).isGreaterThan(0);
        assertThat(cpuSampleCount.get()).isEqualTo(processed);
    }

    @Test
    void shouldHandleEmptyRecordingGracefully() {
        JfrRecordingReader reader = new JfrRecordingReader(emptyRecording);
        JfrRecordingMetadata metadata = reader.readMetadata();

        assertThat(metadata.fileSizeBytes()).isGreaterThanOrEqualTo(0);
        assertThat(metadata.totalEvents()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void shouldThrowWhenFileDoesNotExist() {
        Path missing = tempDir.resolve("non-existent.jfr");
        assertThatThrownBy(() -> new JfrRecordingReader(missing))
            .isInstanceOf(RecordingNotFoundException.class)
            .hasMessageContaining("does not exist");
    }

    @Test
    void shouldThrowWhenFileIsNotJfr() throws IOException {
        Path textFile = tempDir.resolve("test.txt");
        Files.createFile(textFile);

        assertThatThrownBy(() -> new JfrRecordingReader(textFile))
            .isInstanceOf(InvalidRecordingException.class)
            .hasMessageContaining("missing .jfr extension");
    }

    @Test
    void shouldHandleCorruptedFileGracefully() throws IOException {
        Path corrupted = tempDir.resolve("corrupted.jfr");
        Files.writeString(corrupted, "THIS_IS_NOT_A_VALID_JFR_HEADER_STREAM_DATA");

        JfrRecordingReader reader = new JfrRecordingReader(corrupted);
        assertThatThrownBy(reader::readMetadata)
            .isInstanceOf(InvalidRecordingException.class);
    }
}
