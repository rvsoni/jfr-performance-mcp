package com.ravi.jfrmcp.jfr.analyzer;

import com.ravi.jfrmcp.jfr.JfrEventFilter;
import com.ravi.jfrmcp.jfr.JfrEventUtils;
import com.ravi.jfrmcp.jfr.JfrRecordingReader;
import com.ravi.jfrmcp.model.Evidence;
import com.ravi.jfrmcp.model.ExceptionAnalysisResult;
import com.ravi.jfrmcp.model.FindingCategory;
import com.ravi.jfrmcp.model.FindingSeverity;
import com.ravi.jfrmcp.model.PerformanceFinding;
import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Analyzes Java exception throw events and exception statistics.
 */
public class ExceptionAnalyzer implements JfrAnalyzer<ExceptionAnalysisResult> {

    private final int topN;

    public ExceptionAnalyzer() {
        this(10);
    }

    public ExceptionAnalyzer(int topN) {
        this.topN = topN > 0 ? topN : 10;
    }

    @Override
    public String getName() {
        return "ExceptionAnalyzer";
    }

    @Override
    public ExceptionAnalysisResult analyze(JfrRecordingReader reader) {
        String recordingName = reader.getRecordingPath().getFileName().toString();

        Map<String, AtomicLong> exceptionCounts = new HashMap<>();
        Map<String, String> exceptionMessages = new HashMap<>();
        Map<String, Boolean> isErrorMap = new HashMap<>();

        Map<String, AtomicLong> throwingMethods = new HashMap<>();
        Map<String, List<String>> methodStackTraces = new HashMap<>();

        Map<String, AtomicLong> throwingThreads = new HashMap<>();

        long totalExceptionsThrown = 0;
        long totalErrorsThrown = 0;

        JfrEventFilter filter = JfrEventFilter.ofTypes(
            "jdk.JavaExceptionThrow",
            "jdk.JavaErrorThrow",
            "jdk.ExceptionStatistics"
        );

        List<RecordedEvent> events = new ArrayList<>();
        reader.forEachEvent(filter, events::add);

        for (RecordedEvent event : events) {
            String typeName = event.getEventType().getName();

            if ("jdk.ExceptionStatistics".equals(typeName)) {
                if (event.hasField("throwables")) {
                    totalExceptionsThrown = Math.max(totalExceptionsThrown, event.getLong("throwables"));
                }
                continue;
            }

            boolean isError = "jdk.JavaErrorThrow".equals(typeName);
            if (isError) {
                totalErrorsThrown++;
            }
            totalExceptionsThrown++;

            RecordedClass thrownClass = null;
            if (event.hasField("thrownClass")) {
                try {
                    thrownClass = event.getClass("thrownClass");
                } catch (Exception ignored) {}
            }

            String className = thrownClass != null && thrownClass.getName() != null
                ? thrownClass.getName()
                : "java.lang.Throwable";

            String message = JfrEventUtils.getString(event, "message", null);
            String threadName = JfrEventUtils.getThreadName(event);

            exceptionCounts.computeIfAbsent(className, k -> new AtomicLong()).incrementAndGet();
            if (message != null && !exceptionMessages.containsKey(className)) {
                exceptionMessages.put(className, message);
            }
            isErrorMap.putIfAbsent(className, isError);

            throwingThreads.computeIfAbsent(threadName, k -> new AtomicLong()).incrementAndGet();

            List<String> frames = JfrEventUtils.extractStackTrace(event, 1);
            if (!frames.isEmpty()) {
                String method = frames.get(0);
                throwingMethods.computeIfAbsent(method, k -> new AtomicLong()).incrementAndGet();
                if (!methodStackTraces.containsKey(method)) {
                    methodStackTraces.put(method, JfrEventUtils.extractStackTrace(event, 6));
                }
            }
        }

        List<ExceptionAnalysisResult.ExceptionTypeCount> topExceptions = exceptionCounts.entrySet().stream()
            .sorted((a, b) -> Long.compare(b.getValue().get(), a.getValue().get()))
            .limit(topN)
            .map(e -> new ExceptionAnalysisResult.ExceptionTypeCount(
                e.getKey(),
                e.getValue().get(),
                exceptionMessages.get(e.getKey()),
                isErrorMap.getOrDefault(e.getKey(), false)
            ))
            .collect(Collectors.toList());

        List<ExceptionAnalysisResult.ThrowingMethod> topThrowingMethodsList = throwingMethods.entrySet().stream()
            .sorted((a, b) -> Long.compare(b.getValue().get(), a.getValue().get()))
            .limit(topN)
            .map(e -> new ExceptionAnalysisResult.ThrowingMethod(
                e.getKey(),
                e.getValue().get(),
                methodStackTraces.getOrDefault(e.getKey(), Collections.emptyList())
            ))
            .collect(Collectors.toList());

        List<ExceptionAnalysisResult.ThrowingThread> topThrowingThreadsList = throwingThreads.entrySet().stream()
            .sorted((a, b) -> Long.compare(b.getValue().get(), a.getValue().get()))
            .limit(topN)
            .map(e -> new ExceptionAnalysisResult.ThrowingThread(
                e.getKey(),
                e.getValue().get()
            ))
            .collect(Collectors.toList());

        List<PerformanceFinding> findings = new ArrayList<>();

        if (totalErrorsThrown > 0) {
            findings.add(PerformanceFinding.builder()
                .category(FindingCategory.EXCEPTION)
                .severity(FindingSeverity.HIGH)
                .confidence(0.95)
                .title("JVM Error Events Detected (" + totalErrorsThrown + " errors)")
                .summary("One or more java.lang.Error instances (e.g. OutOfMemoryError, LinkageError) were thrown.")
                .evidence(List.of(Evidence.builder()
                    .metric("errorCount")
                    .value(totalErrorsThrown)
                    .unit("errors")
                    .source("jdk.JavaErrorThrow")
                    .confidence(0.99)
                    .description("Uncaught or severe JVM error events")
                    .build()))
                .impact("Errors indicate severe environmental failures, resource exhaustion, or unrecoverable conditions.")
                .recommendedInvestigation(List.of(
                    "Examine stack traces of jdk.JavaErrorThrow events",
                    "Verify system memory and classloader state"
                ))
                .build());
        }

        if (!topExceptions.isEmpty()) {
            ExceptionAnalysisResult.ExceptionTypeCount topEx = topExceptions.get(0);
            if (topEx.count() > 500) {
                findings.add(PerformanceFinding.builder()
                    .category(FindingCategory.EXCEPTION)
                    .severity(FindingSeverity.MEDIUM)
                    .confidence(0.90)
                    .title("Exception Storm: " + topEx.count() + " " + topEx.exceptionClass() + " instances thrown")
                    .summary("Frequent exception instantiation creates CPU overhead from stack trace construction.")
                    .evidence(List.of(Evidence.builder()
                        .metric("exceptionCount")
                        .value(topEx.count())
                        .unit("throws")
                        .source("jdk.JavaExceptionThrow")
                        .confidence(0.95)
                        .description("High rate of " + topEx.exceptionClass() + " thrown during recording")
                        .build()))
                    .impact("Constructing Throwable stack traces takes non-trivial CPU time and generates short-lived heap allocation.")
                    .recommendedInvestigation(List.of(
                        "Investigate why " + topEx.exceptionClass() + " is thrown in normal flow",
                        "Avoid using exceptions for normal control flow",
                        "Consider pre-allocated / disabled-stack exceptions if used as signaling"
                    ))
                    .build());
            }
        }

        return new ExceptionAnalysisResult(
            recordingName,
            totalExceptionsThrown,
            totalErrorsThrown,
            topExceptions,
            topThrowingMethodsList,
            topThrowingThreadsList,
            findings
        );
    }
}
