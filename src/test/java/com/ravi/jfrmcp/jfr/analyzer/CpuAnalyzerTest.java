package com.ravi.jfrmcp.jfr.analyzer;

import com.ravi.jfrmcp.TestRecordingFactory;
import com.ravi.jfrmcp.jfr.JfrRecordingReader;
import com.ravi.jfrmcp.model.CpuAnalysisResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CpuAnalyzerTest {

    private static Path realisticRecording;
    private static Path emptyRecording;

    @TempDir
    static Path tempDir;

    @BeforeAll
    static void setUpAll() throws Exception {
        realisticRecording = TestRecordingFactory.createRealisticRecording(tempDir.resolve("cpu-test.jfr"));
        emptyRecording = TestRecordingFactory.createEmptyRecording();
    }

    @Test
    void shouldIdentifyExpectedCpuHotspotInRealisticRecording() {
        CpuAnalyzer analyzer = new CpuAnalyzer(5);
        CpuAnalysisResult result = analyzer.analyze(new JfrRecordingReader(realisticRecording));

        assertThat(result.recording()).isEqualTo(realisticRecording.getFileName().toString());
        assertThat(result.totalSamples()).isGreaterThan(0);
        assertThat(result.topMethods()).isNotEmpty();
        assertThat(result.evidenceNote()).contains("Distinguish statistical sample evidence");

        // The top method should be calculatePrice
        CpuAnalysisResult.MethodSample top = result.topMethods().get(0);
        assertThat(top.method()).contains("calculatePrice");
        assertThat(top.percentage()).isGreaterThan(30.0);
    }

    @Test
    void shouldRespectTopNLimit() {
        CpuAnalyzer analyzer = new CpuAnalyzer(2);
        CpuAnalysisResult result = analyzer.analyze(new JfrRecordingReader(realisticRecording));

        assertThat(result.topMethods().size()).isLessThanOrEqualTo(2);
        assertThat(result.topClasses().size()).isLessThanOrEqualTo(2);
        assertThat(result.topPackages().size()).isLessThanOrEqualTo(2);
    }

    @Test
    void shouldHandleEmptyRecordingWithoutErrors() {
        CpuAnalyzer analyzer = new CpuAnalyzer(10);
        CpuAnalysisResult result = analyzer.analyze(new JfrRecordingReader(emptyRecording));

        assertThat(result.totalSamples()).isEqualTo(0);
        assertThat(result.topMethods()).isEmpty();
        assertThat(result.findings()).isEmpty();
    }
}
