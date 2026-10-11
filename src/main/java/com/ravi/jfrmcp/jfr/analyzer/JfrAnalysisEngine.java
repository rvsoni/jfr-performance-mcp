package com.ravi.jfrmcp.jfr.analyzer;

import com.ravi.jfrmcp.config.JfrConfiguration;
import com.ravi.jfrmcp.jfr.InMemoryRecordingAnalysisCache;
import com.ravi.jfrmcp.jfr.JfrRecordingReader;
import com.ravi.jfrmcp.jfr.RecordingAnalysisCache;
import com.ravi.jfrmcp.model.AllocationAnalysisResult;
import com.ravi.jfrmcp.model.CpuAnalysisResult;
import com.ravi.jfrmcp.model.ExceptionAnalysisResult;
import com.ravi.jfrmcp.model.FindingSeverity;
import com.ravi.jfrmcp.model.FullAnalysisResult;
import com.ravi.jfrmcp.model.GcAnalysisResult;
import com.ravi.jfrmcp.model.IoAnalysisResult;
import com.ravi.jfrmcp.model.LatencyAnalysisResult;
import com.ravi.jfrmcp.model.LockAnalysisResult;
import com.ravi.jfrmcp.model.PerformanceFinding;
import com.ravi.jfrmcp.model.RecordingSummary;
import com.ravi.jfrmcp.model.ThreadAnalysisResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Core JFR Analysis Engine coordinating domain analyzers and result caching.
 * Independent of MCP and presentation layers.
 */
public class JfrAnalysisEngine {

    private static final Logger log = LoggerFactory.getLogger(JfrAnalysisEngine.class);

    private final JfrConfiguration configuration;
    private final RecordingAnalysisCache cache;

    public JfrAnalysisEngine() {
        this(new JfrConfiguration(), new InMemoryRecordingAnalysisCache());
    }

    public JfrAnalysisEngine(JfrConfiguration configuration, RecordingAnalysisCache cache) {
        this.configuration = Objects.requireNonNull(configuration, "configuration cannot be null");
        this.cache = Objects.requireNonNull(cache, "cache cannot be null");
    }

    public RecordingSummary getSummary(Path recordingPath) {
        return executeWithCache(recordingPath, RecordingSummary.class, () ->
            new RecordingSummaryAnalyzer().analyze(new JfrRecordingReader(recordingPath))
        );
    }

    public CpuAnalysisResult analyzeCpu(Path recordingPath, int topN) {
        int boundedTopN = Math.min(topN > 0 ? topN : 10, configuration.getMaxTopN());
        return executeWithCache(recordingPath, CpuAnalysisResult.class, () ->
            new CpuAnalyzer(boundedTopN).analyze(new JfrRecordingReader(recordingPath))
        );
    }

    public GcAnalysisResult analyzeGc(Path recordingPath) {
        return executeWithCache(recordingPath, GcAnalysisResult.class, () ->
            new GarbageCollectionAnalyzer().analyze(new JfrRecordingReader(recordingPath))
        );
    }

    public AllocationAnalysisResult analyzeAllocation(Path recordingPath, int topN) {
        int boundedTopN = Math.min(topN > 0 ? topN : 10, configuration.getMaxTopN());
        return executeWithCache(recordingPath, AllocationAnalysisResult.class, () ->
            new AllocationAnalyzer(boundedTopN).analyze(new JfrRecordingReader(recordingPath))
        );
    }

    public ThreadAnalysisResult analyzeThreads(Path recordingPath) {
        return executeWithCache(recordingPath, ThreadAnalysisResult.class, () ->
            new ThreadAnalyzer().analyze(new JfrRecordingReader(recordingPath))
        );
    }

    public LockAnalysisResult analyzeLocks(Path recordingPath, int topN) {
        int boundedTopN = Math.min(topN > 0 ? topN : 10, configuration.getMaxTopN());
        return executeWithCache(recordingPath, LockAnalysisResult.class, () ->
            new LockAnalyzer(boundedTopN).analyze(new JfrRecordingReader(recordingPath))
        );
    }

    public ExceptionAnalysisResult analyzeExceptions(Path recordingPath, int topN) {
        int boundedTopN = Math.min(topN > 0 ? topN : 10, configuration.getMaxTopN());
        return executeWithCache(recordingPath, ExceptionAnalysisResult.class, () ->
            new ExceptionAnalyzer(boundedTopN).analyze(new JfrRecordingReader(recordingPath))
        );
    }

    public IoAnalysisResult analyzeIo(Path recordingPath, int topN) {
        int boundedTopN = Math.min(topN > 0 ? topN : 10, configuration.getMaxTopN());
        return executeWithCache(recordingPath, IoAnalysisResult.class, () ->
            new IoAnalyzer(boundedTopN).analyze(new JfrRecordingReader(recordingPath))
        );
    }

    public LatencyAnalysisResult analyzeLatency(Path recordingPath, double thresholdMs) {
        return executeWithCache(recordingPath, LatencyAnalysisResult.class, () ->
            new LatencyAnalyzer(thresholdMs).analyze(new JfrRecordingReader(recordingPath))
        );
    }

    public FullAnalysisResult analyzeRecording(Path recordingPath) {
        return executeWithCache(recordingPath, FullAnalysisResult.class, () -> {
            long startTime = System.currentTimeMillis();
            log.info("Starting comprehensive multi-dimensional analysis for {}", recordingPath.getFileName());

            JfrRecordingReader reader = new JfrRecordingReader(recordingPath);

            RecordingSummary summary = new RecordingSummaryAnalyzer().analyze(reader);
            CpuAnalysisResult cpu = new CpuAnalyzer(10).analyze(reader);
            GcAnalysisResult gc = new GarbageCollectionAnalyzer().analyze(reader);
            AllocationAnalysisResult allocation = new AllocationAnalyzer(10).analyze(reader);
            ThreadAnalysisResult threads = new ThreadAnalyzer().analyze(reader);
            LockAnalysisResult locks = new LockAnalyzer(10).analyze(reader);
            ExceptionAnalysisResult exceptions = new ExceptionAnalyzer(10).analyze(reader);
            IoAnalysisResult io = new IoAnalyzer(10).analyze(reader);
            LatencyAnalysisResult latency = new LatencyAnalyzer(100.0).analyze(reader);

            List<PerformanceFinding> allFindings = new ArrayList<>();
            allFindings.addAll(cpu.findings());
            allFindings.addAll(gc.findings());
            allFindings.addAll(allocation.findings());
            allFindings.addAll(threads.findings());
            allFindings.addAll(locks.findings());
            allFindings.addAll(exceptions.findings());
            allFindings.addAll(io.findings());
            allFindings.addAll(latency.findings());

            // Sort findings by severity (CRITICAL > HIGH > MEDIUM > LOW > INFO) and confidence
            allFindings.sort(Comparator
                .comparing(PerformanceFinding::severity, Comparator.reverseOrder())
                .thenComparing(Comparator.comparingDouble(PerformanceFinding::confidence).reversed())
            );

            FindingSeverity overallAssessment = allFindings.isEmpty()
                ? FindingSeverity.INFO
                : allFindings.get(0).severity();

            // Evaluate healthy areas where no significant degradation was observed
            List<String> healthyAreas = new ArrayList<>();
            if (gc.findings().isEmpty()) {
                healthyAreas.add("GC pause times appear normal (max pause: " + gc.maxPauseMs() + "ms, total pause: " + gc.totalPauseMs() + "ms)");
            }
            if (locks.findings().isEmpty()) {
                healthyAreas.add("No significant Java monitor lock contention detected (" + locks.totalContentionEvents() + " events)");
            }
            if (cpu.findings().isEmpty()) {
                healthyAreas.add("CPU utilization and execution sample distribution appear balanced without extreme single-method hotspots");
            }
            if (exceptions.findings().isEmpty()) {
                healthyAreas.add("No exception storm or JVM fatal error events detected");
            }
            if (io.findings().isEmpty()) {
                healthyAreas.add("File and socket I/O latency remained within acceptable operational bounds");
            }

            // Generate actionable, correlated next steps
            List<String> nextSteps = new ArrayList<>();
            for (PerformanceFinding finding : allFindings) {
                if (finding.severity() == FindingSeverity.CRITICAL || finding.severity() == FindingSeverity.HIGH) {
                    nextSteps.addAll(finding.recommendedInvestigation());
                }
            }
            if (nextSteps.isEmpty()) {
                for (PerformanceFinding finding : allFindings) {
                    if (finding.severity() == FindingSeverity.MEDIUM) {
                        nextSteps.addAll(finding.recommendedInvestigation());
                    }
                }
            }
            if (nextSteps.isEmpty()) {
                nextSteps.add("No critical JVM performance issues detected. Continue routine observability monitoring.");
            }

            // Deduplicate next steps while preserving order
            List<String> distinctNextSteps = nextSteps.stream().distinct().limit(8).toList();

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("Completed analysis for {} in {} ms with {} findings (overall: {})",
                recordingPath.getFileName(), elapsed, allFindings.size(), overallAssessment);

            return new FullAnalysisResult(
                recordingPath.getFileName().toString(),
                overallAssessment,
                allFindings,
                healthyAreas,
                distinctNextSteps,
                summary,
                cpu,
                gc,
                allocation,
                threads,
                locks,
                exceptions,
                io,
                latency
            );
        });
    }

    @FunctionalInterface
    private interface SupplierWithException<T> {
        T get() throws Exception;
    }

    private <T> T executeWithCache(Path recordingPath, Class<T> resultClass, SupplierWithException<T> supplier) {
        if (!configuration.isCacheEnabled()) {
            try {
                return supplier.get();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException("Analysis execution failed: " + e.getMessage(), e);
            }
        }

        Optional<RecordingAnalysisCache.CacheKey> optKey = RecordingAnalysisCache.CacheKey.of(recordingPath);
        if (optKey.isPresent()) {
            RecordingAnalysisCache.CacheKey key = optKey.get();
            Optional<T> cached = cache.get(key, resultClass);
            if (cached.isPresent()) {
                log.debug("Cache hit for {} ({})", recordingPath.getFileName(), resultClass.getSimpleName());
                return cached.get();
            }

            try {
                T result = supplier.get();
                cache.put(key, resultClass, result);
                return result;
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException("Analysis execution failed: " + e.getMessage(), e);
            }
        }

        try {
            return supplier.get();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Analysis execution failed: " + e.getMessage(), e);
        }
    }

    public JfrConfiguration getConfiguration() {
        return configuration;
    }

    public RecordingAnalysisCache getCache() {
        return cache;
    }
}
