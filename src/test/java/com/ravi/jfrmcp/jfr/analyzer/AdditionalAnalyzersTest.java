package com.ravi.jfrmcp.jfr.analyzer;

import com.ravi.jfrmcp.TestRecordingFactory;
import com.ravi.jfrmcp.jfr.JfrRecordingReader;
import com.ravi.jfrmcp.model.AllocationAnalysisResult;
import com.ravi.jfrmcp.model.ExceptionAnalysisResult;
import com.ravi.jfrmcp.model.ThreadAnalysisResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AdditionalAnalyzersTest {

    private static Path realisticRecording;
    private static Path emptyRecording;

    @TempDir
    static Path tempDir;

    @BeforeAll
    static void setUpAll() throws Exception {
        realisticRecording = TestRecordingFactory.createRealisticRecording(tempDir.resolve("more-tests.jfr"));
        emptyRecording = TestRecordingFactory.createEmptyRecording();
    }

    @Test
    void shouldAnalyzeThreads() {
        ThreadAnalyzer analyzer = new ThreadAnalyzer();
        ThreadAnalysisResult result = analyzer.analyze(new JfrRecordingReader(realisticRecording));

        assertThat(result.totalThreadCount()).isGreaterThan(0);
        assertThat(result.threads()).isNotEmpty();
    }

    @Test
    void shouldAnalyzeAllocations() {
        AllocationAnalyzer analyzer = new AllocationAnalyzer(5);
        AllocationAnalysisResult result = analyzer.analyze(new JfrRecordingReader(realisticRecording));

        assertThat(result.totalAllocationSamples()).isGreaterThan(0);
        assertThat(result.topAllocatingClasses()).isNotEmpty();
        assertThat(result.estimationNote()).contains("TLAB");
    }

    @Test
    void shouldAnalyzeExceptions() {
        ExceptionAnalyzer analyzer = new ExceptionAnalyzer(5);
        ExceptionAnalysisResult result = analyzer.analyze(new JfrRecordingReader(realisticRecording));

        assertThat(result.totalExceptionsThrown()).isGreaterThan(0);
        assertThat(result.topExceptions()).isNotEmpty();
        assertThat(result.topExceptions().get(0).exceptionClass()).contains("IllegalArgumentException");
    }

    @Test
    void shouldHandleEmptyRecordingAcrossAnalyzers() {
        ThreadAnalysisResult threads = new ThreadAnalyzer().analyze(new JfrRecordingReader(emptyRecording));
        assertThat(threads.totalThreadCount()).isGreaterThanOrEqualTo(0);

        AllocationAnalysisResult allocs = new AllocationAnalyzer().analyze(new JfrRecordingReader(emptyRecording));
        assertThat(allocs.totalAllocationSamples()).isEqualTo(0);

        ExceptionAnalysisResult ex = new ExceptionAnalyzer().analyze(new JfrRecordingReader(emptyRecording));
        assertThat(ex.totalExceptionsThrown()).isEqualTo(0);
    }
}
