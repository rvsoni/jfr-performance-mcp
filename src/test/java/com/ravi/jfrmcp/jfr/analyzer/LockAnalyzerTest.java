package com.ravi.jfrmcp.jfr.analyzer;

import com.ravi.jfrmcp.TestRecordingFactory;
import com.ravi.jfrmcp.jfr.JfrRecordingReader;
import com.ravi.jfrmcp.model.LockAnalysisResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LockAnalyzerTest {

    private static Path realisticRecording;
    private static Path emptyRecording;

    @TempDir
    static Path tempDir;

    @BeforeAll
    static void setUpAll() throws Exception {
        realisticRecording = TestRecordingFactory.createRealisticRecording(tempDir.resolve("lock-test.jfr"));
        emptyRecording = TestRecordingFactory.createEmptyRecording();
    }

    @Test
    void shouldIdentifyLockContentionInRealisticRecording() {
        LockAnalyzer analyzer = new LockAnalyzer(5);
        LockAnalysisResult result = analyzer.analyze(new JfrRecordingReader(realisticRecording));

        assertThat(result.recording()).isEqualTo(realisticRecording.getFileName().toString());
        assertThat(result.totalContentionEvents()).isGreaterThan(0);
        assertThat(result.contentions()).isNotEmpty();

        LockAnalysisResult.LockContentionSummary top = result.contentions().get(0);
        assertThat(top.blockedThreads()).isGreaterThan(0);
        assertThat(top.blockedTimeMs()).isGreaterThan(0.0);
    }

    @Test
    void shouldRespectTopN() {
        LockAnalyzer analyzer = new LockAnalyzer(1);
        LockAnalysisResult result = analyzer.analyze(new JfrRecordingReader(realisticRecording));

        assertThat(result.contentions().size()).isLessThanOrEqualTo(1);
    }

    @Test
    void shouldHandleEmptyRecordingWithoutErrors() {
        LockAnalyzer analyzer = new LockAnalyzer(10);
        LockAnalysisResult result = analyzer.analyze(new JfrRecordingReader(emptyRecording));

        assertThat(result.totalContentionEvents()).isEqualTo(0);
        assertThat(result.contentions()).isEmpty();
        assertThat(result.findings()).isEmpty();
    }
}
