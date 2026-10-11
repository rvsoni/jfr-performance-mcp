package com.ravi.jfrmcp.jfr.analyzer;

import com.ravi.jfrmcp.TestRecordingFactory;
import com.ravi.jfrmcp.jfr.JfrRecordingReader;
import com.ravi.jfrmcp.model.GcAnalysisResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class GarbageCollectionAnalyzerTest {

    private static Path realisticRecording;
    private static Path emptyRecording;

    @TempDir
    static Path tempDir;

    @BeforeAll
    static void setUpAll() throws Exception {
        realisticRecording = TestRecordingFactory.createRealisticRecording(tempDir.resolve("gc-test.jfr"));
        emptyRecording = TestRecordingFactory.createEmptyRecording();
    }

    @Test
    void shouldAnalyzeGcEventsInRealisticRecording() {
        GarbageCollectionAnalyzer analyzer = new GarbageCollectionAnalyzer();
        GcAnalysisResult result = analyzer.analyze(new JfrRecordingReader(realisticRecording));

        assertThat(result.recording()).isEqualTo(realisticRecording.getFileName().toString());
        assertThat(result.assessment()).isIn("LOW", "MEDIUM", "HIGH", "CRITICAL");
        assertThat(result.assessmentRules()).contains("GC Assessment Rules");
    }

    @Test
    void shouldHandleEmptyRecordingWithoutErrors() {
        GarbageCollectionAnalyzer analyzer = new GarbageCollectionAnalyzer();
        GcAnalysisResult result = analyzer.analyze(new JfrRecordingReader(emptyRecording));

        assertThat(result.gcCount()).isEqualTo(0);
        assertThat(result.totalPauseMs()).isEqualTo(0.0);
        assertThat(result.maxPauseMs()).isEqualTo(0.0);
        assertThat(result.assessment()).isEqualTo("LOW");
        assertThat(result.findings()).isEmpty();
    }
}
