package com.ravi.jfrmcp.jfr.analyzer;

import com.ravi.jfrmcp.TestRecordingFactory;
import com.ravi.jfrmcp.config.JfrConfiguration;
import com.ravi.jfrmcp.jfr.InMemoryRecordingAnalysisCache;
import com.ravi.jfrmcp.model.FullAnalysisResult;
import com.ravi.jfrmcp.model.RecordingSummary;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class JfrAnalysisEngineTest {

    private static Path realisticRecording;

    @TempDir
    static Path tempDir;

    @BeforeAll
    static void setUpAll() throws Exception {
        realisticRecording = TestRecordingFactory.createRealisticRecording(tempDir.resolve("engine-test.jfr"));
    }

    @Test
    void shouldPerformComprehensiveCorrelatedAnalysis() {
        JfrConfiguration config = new JfrConfiguration(tempDir, 10, true, 1024 * 1024 * 1024L, java.time.Duration.ofSeconds(30));
        InMemoryRecordingAnalysisCache cache = new InMemoryRecordingAnalysisCache();
        JfrAnalysisEngine engine = new JfrAnalysisEngine(config, cache);

        FullAnalysisResult result = engine.analyzeRecording(realisticRecording);

        assertThat(result.recording()).isEqualTo(realisticRecording.getFileName().toString());
        assertThat(result.overallAssessment()).isNotNull();
        assertThat(result.findings()).isNotEmpty();
        assertThat(result.summary()).isNotNull();
        assertThat(result.cpu()).isNotNull();
        assertThat(result.gc()).isNotNull();
        assertThat(result.locks()).isNotNull();
        assertThat(result.nextSteps()).isNotEmpty();

        // Verify caching: second call returns fast from cache
        FullAnalysisResult second = engine.analyzeRecording(realisticRecording);
        assertThat(second).isSameAs(result);
    }

    @Test
    void shouldRetrieveSummaryWithCache() {
        JfrConfiguration config = new JfrConfiguration();
        InMemoryRecordingAnalysisCache cache = new InMemoryRecordingAnalysisCache();
        JfrAnalysisEngine engine = new JfrAnalysisEngine(config, cache);

        RecordingSummary summary1 = engine.getSummary(realisticRecording);
        RecordingSummary summary2 = engine.getSummary(realisticRecording);

        assertThat(summary1).isNotNull();
        assertThat(summary1.fileName()).isEqualTo(realisticRecording.getFileName().toString());
        assertThat(summary2).isSameAs(summary1);
    }
}
