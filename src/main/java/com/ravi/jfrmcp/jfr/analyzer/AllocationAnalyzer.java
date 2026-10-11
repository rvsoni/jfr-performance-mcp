package com.ravi.jfrmcp.jfr.analyzer;

import com.ravi.jfrmcp.jfr.JfrEventFilter;
import com.ravi.jfrmcp.jfr.JfrEventUtils;
import com.ravi.jfrmcp.jfr.JfrRecordingMetadata;
import com.ravi.jfrmcp.jfr.JfrRecordingReader;
import com.ravi.jfrmcp.model.AllocationAnalysisResult;
import com.ravi.jfrmcp.model.Evidence;
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
 * Analyzes object allocation pressure, allocation hotspots, and estimated allocation rates from JFR events.
 */
public class AllocationAnalyzer implements JfrAnalyzer<AllocationAnalysisResult> {

    private final int topN;

    public AllocationAnalyzer() {
        this(10);
    }

    public AllocationAnalyzer(int topN) {
        this.topN = topN > 0 ? topN : 10;
    }

    @Override
    public String getName() {
        return "AllocationAnalyzer";
    }

    @Override
    public AllocationAnalysisResult analyze(JfrRecordingReader reader) {
        String recordingName = reader.getRecordingPath().getFileName().toString();

        Map<String, AtomicLong> classSampleCounts = new HashMap<>();
        Map<String, AtomicLong> classEstimatedBytes = new HashMap<>();

        Map<String, AtomicLong> threadSampleCounts = new HashMap<>();
        Map<String, AtomicLong> threadEstimatedBytes = new HashMap<>();

        long totalSampleCount = 0;
        long totalEstimatedBytes = 0;

        JfrEventFilter filter = JfrEventFilter.ofTypes(
            "jdk.ObjectAllocationInNewTLAB",
            "jdk.ObjectAllocationOutsideTLAB",
            "jdk.ObjectAllocationSample"
        );

        List<RecordedEvent> events = new ArrayList<>();
        reader.forEachEvent(filter, events::add);

        for (RecordedEvent event : events) {
            String typeName = event.getEventType().getName();
            long bytes = 0;

            if ("jdk.ObjectAllocationInNewTLAB".equals(typeName)) {
                // tlabSize is estimated chunk allocated
                bytes = JfrEventUtils.getLong(event, "tlabSize", 0);
                if (bytes == 0) {
                    bytes = JfrEventUtils.getLong(event, "allocationSize", 0);
                }
            } else if ("jdk.ObjectAllocationOutsideTLAB".equals(typeName)) {
                bytes = JfrEventUtils.getLong(event, "allocationSize", 0);
            } else if ("jdk.ObjectAllocationSample".equals(typeName)) {
                bytes = JfrEventUtils.getLong(event, "weight", 0);
                if (bytes == 0) {
                    bytes = JfrEventUtils.getLong(event, "allocationSize", 0);
                }
            }

            RecordedClass objectClass = null;
            if (event.hasField("objectClass")) {
                try {
                    objectClass = event.getClass("objectClass");
                } catch (Exception ignored) {}
            }

            String className = objectClass != null && objectClass.getName() != null ? objectClass.getName() : "UnknownType";
            String threadName = JfrEventUtils.getThreadName(event);

            totalSampleCount++;
            totalEstimatedBytes += bytes;

            classSampleCounts.computeIfAbsent(className, k -> new AtomicLong()).incrementAndGet();
            classEstimatedBytes.computeIfAbsent(className, k -> new AtomicLong()).addAndGet(bytes);

            threadSampleCounts.computeIfAbsent(threadName, k -> new AtomicLong()).incrementAndGet();
            threadEstimatedBytes.computeIfAbsent(threadName, k -> new AtomicLong()).addAndGet(bytes);
        }

        final long grandTotalBytes = totalEstimatedBytes;

        List<AllocationAnalysisResult.AllocatingClass> topClasses = classEstimatedBytes.entrySet().stream()
            .sorted((a, b) -> Long.compare(b.getValue().get(), a.getValue().get()))
            .limit(topN)
            .map(e -> {
                double pct = grandTotalBytes > 0 ? (e.getValue().get() * 100.0) / grandTotalBytes : 0.0;
                long samples = classSampleCounts.getOrDefault(e.getKey(), new AtomicLong(0)).get();
                return new AllocationAnalysisResult.AllocatingClass(
                    e.getKey(),
                    samples,
                    e.getValue().get(),
                    Math.round(pct * 100.0) / 100.0
                );
            })
            .collect(Collectors.toList());

        List<AllocationAnalysisResult.ThreadAllocation> topThreads = threadEstimatedBytes.entrySet().stream()
            .sorted((a, b) -> Long.compare(b.getValue().get(), a.getValue().get()))
            .limit(topN)
            .map(e -> {
                double pct = grandTotalBytes > 0 ? (e.getValue().get() * 100.0) / grandTotalBytes : 0.0;
                long samples = threadSampleCounts.getOrDefault(e.getKey(), new AtomicLong(0)).get();
                return new AllocationAnalysisResult.ThreadAllocation(
                    e.getKey(),
                    samples,
                    e.getValue().get(),
                    Math.round(pct * 100.0) / 100.0
                );
            })
            .collect(Collectors.toList());

        // Derive estimated allocation rate (MB/sec) using recording duration
        Double durationSec = null;
        try {
            JfrRecordingMetadata meta = reader.readMetadata();
            durationSec = meta.durationSeconds();
        } catch (Exception ignored) {}

        Double allocRateMbPerSec = null;
        if (durationSec != null && durationSec > 0 && grandTotalBytes > 0) {
            double totalMb = grandTotalBytes / (1024.0 * 1024.0);
            allocRateMbPerSec = Math.round((totalMb / durationSec) * 100.0) / 100.0;
        }

        List<PerformanceFinding> findings = new ArrayList<>();

        if (!topClasses.isEmpty() && grandTotalBytes > 10 * 1024 * 1024) {
            AllocationAnalysisResult.AllocatingClass topClass = topClasses.get(0);
            if (topClass.percentage() >= 35.0) {
                Evidence ev = Evidence.builder()
                    .metric("dominantAllocatingClassPercentage")
                    .value(topClass.percentage())
                    .unit("percent_of_sampled_bytes")
                    .source("jdk.ObjectAllocationInNewTLAB / OutsideTLAB")
                    .confidence(0.90)
                    .description("Class " + topClass.className() + " accounted for " + topClass.percentage() + "% of sampled allocation bytes (~" + (topClass.estimatedBytes() / (1024 * 1024)) + " MB)")
                    .build();

                findings.add(PerformanceFinding.builder()
                    .category(FindingCategory.ALLOCATION)
                    .severity(topClass.percentage() >= 55.0 ? FindingSeverity.HIGH : FindingSeverity.MEDIUM)
                    .confidence(0.88)
                    .title("Allocation Hotspot Concentrated in " + topClass.className())
                    .summary("A disproportionate volume of heap memory allocation is driven by " + topClass.className() + ".")
                    .evidence(List.of(ev))
                    .impact("High allocation rates saturate Young generation TLABs rapidly, causing frequent GC cycles and increased CPU overhead.")
                    .recommendedInvestigation(List.of(
                        "Investigate where " + topClass.className() + " instances are instantiated",
                        "Evaluate object reuse, pooling, flyweight pattern, or zero-copy alternatives (e.g. ByteBuffer slice vs byte[] copying)",
                        "Check for redundant defensive copies or unnecessary wrapper boxing"
                    ))
                    .build());
            }
        }

        if (allocRateMbPerSec != null && allocRateMbPerSec > 500.0) {
            findings.add(PerformanceFinding.builder()
                .category(FindingCategory.ALLOCATION)
                .severity(FindingSeverity.HIGH)
                .confidence(0.85)
                .title("Very High Memory Allocation Rate (~" + allocRateMbPerSec + " MB/s)")
                .summary("Application allocates memory at an intense rate, likely inducing frequent young-generation garbage collections.")
                .evidence(List.of(Evidence.builder()
                    .metric("estimatedAllocationRate")
                    .value(allocRateMbPerSec)
                    .unit("MB/s")
                    .source("JFR Allocation Events")
                    .confidence(0.85)
                    .description("Estimated rate based on sampled TLAB expansions and outside-TLAB allocations")
                    .build()))
                .impact("Elevated GC frequency and cache thrashing.")
                .recommendedInvestigation(List.of(
                    "Inspect top allocating threads and methods",
                    "Profile short-lived allocations using JMC or async-profiler"
                ))
                .build());
        }

        String note = "Allocation metrics are derived from sampled events (New TLAB allocation and Outside TLAB allocation). " +
            "Values represent estimated sampled volume and relative distribution, NOT byte-exact total heap allocation.";

        return new AllocationAnalysisResult(
            recordingName,
            totalSampleCount,
            grandTotalBytes,
            allocRateMbPerSec,
            topClasses,
            topThreads,
            findings,
            note
        );
    }
}
