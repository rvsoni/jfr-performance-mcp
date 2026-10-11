# MCP Tools Reference: jfr-performance-mcp

`jfr-performance-mcp` exposes 11 production-quality MCP tools over STDIO transport for offline investigation of Java Flight Recorder (`.jfr`) recordings.

## Tool Index

| Tool | Purpose | Primary Evidence Extracted |
|------|---------|---------------------------|
| [`list_recordings`](#1-list_recordings) | Discovers available `.jfr` recordings | Filenames, paths, sizes, readable flags |
| [`recording_summary`](#2-recording_summary) | Environment, JVM metadata & timeframe | Java version, JVM args, OS, CPU count, event counts |
| [`analyze_cpu`](#3-analyze_cpu) | CPU profiling & execution sample analysis | Top methods, packages, classes, thread CPU shares |
| [`analyze_gc`](#4-analyze_gc) | Garbage collection & heap memory dynamics | GC pause durations, young/old counts, heap occupancy |
| [`analyze_allocation`](#5-analyze_allocation) | Object allocation hotspots & rates | Top allocating classes, thread allocation, MB/s rate |
| [`analyze_threads`](#6-analyze_threads) | Thread states & concurrency profiling | RUNNABLE, BLOCKED, WAITING, PARKED counts & threads |
| [`analyze_locks`](#7-analyze_locks) | Synchronization & monitor contention | Contended classes, blocked threads, cumulative wait time |
| [`analyze_exceptions`](#8-analyze_exceptions) | Exception storms & JVM errors | Thrown exception counts, top throwers, fatal errors |
| [`analyze_io`](#9-analyze_io) | Socket & File I/O latency | Socket/file read/write durations, slowest operations |
| [`analyze_latency`](#10-analyze_latency) | Latency outliers exceeding threshold | Events exceeding threshold (default 100ms) |
| [`analyze_recording`](#11-analyze_recording) | Multi-dimensional correlation & triage | Correlated findings, confidence scores, healthy areas, next steps |

---

## Tool Details

### 1. `list_recordings`
- **Purpose**: Discovers available `.jfr` recordings inside the configured directory.
- **Parameters**:
  - `directory` *(string, optional)*: Relative subdirectory to scan.
- **Example Response**:
```json
{
  "recordings": [
    {
      "fileName": "demo-production.jfr",
      "path": "demo-production.jfr",
      "sizeBytes": 148342,
      "lastModified": "2026-10-06T19:00:05Z",
      "readable": true
    }
  ]
}
```

---

### 2. `recording_summary`
- **Purpose**: Inspects recording metadata, timeframe, JVM configuration, and event counts.
- **Parameters**:
  - `recording` *(string, required)*: Recording filename.
- **Example Response**:
```json
{
  "fileName": "demo-production.jfr",
  "fileSizeBytes": 148342,
  "startTime": "2026-10-06T19:00:00.123Z",
  "endTime": "2026-10-06T19:00:01.456Z",
  "durationSeconds": 1.33,
  "jvm": {
    "version": "25.0.2+10-LTS",
    "vendor": "Eclipse Adoptium",
    "vmName": "OpenJDK 64-Bit Server VM",
    "cpuCount": 16
  },
  "eventCount": 4210,
  "eventTypeCount": 24,
  "eventTypes": ["jdk.ExecutionSample", "jdk.JavaMonitorEnter", "jdk.CPULoad"]
}
```

---

### 3. `analyze_cpu`
- **Purpose**: Evaluates execution samples and CPU load metrics to locate CPU hotspots.
- **Parameters**:
  - `recording` *(string, required)*: Recording filename.
  - `topN` *(integer, optional, default: 10)*: Maximum number of hotspots to return.
- **Example Response**:
```json
{
  "recording": "demo-production.jfr",
  "totalSamples": 1850,
  "topMethods": [
    {
      "method": "com.ravi.jfrmcp.TestRecordingFactory$OrderService.calculatePrice()",
      "samples": 1780,
      "percentage": 96.22,
      "isNative": false,
      "sampleStackTrace": [
        "com.ravi.jfrmcp.TestRecordingFactory$OrderService.calculatePrice():48"
      ]
    }
  ],
  "averageJvmUserCpu": 0.88,
  "evidenceNote": "Percentages represent statistical share of captured execution samples, NOT guaranteed continuous CPU time."
}
```

---

### 4. `analyze_gc`
- **Purpose**: Evaluates stop-the-world pauses, young vs. old collections, and heap occupancy.
- **Parameters**:
  - `recording` *(string, required)*: Recording filename.
- **Assessment Criteria**:
  - `CRITICAL`: Max pause > 1000ms, Old/Full GC > 10, or post-GC heap occupancy > 90%.
  - `HIGH`: Max pause > 500ms, Old/Full GC > 3, post-GC heap occupancy > 80%, or total pause > 5000ms.
  - `MEDIUM`: Max pause > 200ms, post-GC heap occupancy > 70%, or total pause > 1500ms.
  - `LOW`: Normal GC activity within thresholds.
- **Example Response**:
```json
{
  "recording": "demo-production.jfr",
  "gcCount": 2,
  "totalPauseMs": 14.5,
  "maxPauseMs": 9.2,
  "averagePauseMs": 7.25,
  "youngGcCount": 2,
  "oldGcCount": 0,
  "assessment": "LOW",
  "assessmentRules": "GC Assessment Rules: CRITICAL: Max pause > 1000ms...",
  "findings": []
}
```

---

### 5. `analyze_allocation`
- **Purpose**: Evaluates memory allocation rate and top allocating classes/threads.
- **Parameters**:
  - `recording` *(string, required)*: Recording filename.
  - `topN` *(integer, optional, default: 10)*: Maximum items to return.
- **Example Response**:
```json
{
  "recording": "demo-production.jfr",
  "totalAllocationSamples": 520,
  "estimatedTotalAllocatedBytes": 4259840,
  "estimatedAllocationRateMbPerSec": 3.05,
  "topAllocatingClasses": [
    {
      "className": "com.ravi.jfrmcp.TestRecordingFactory$DummyOrderPayload",
      "sampleCount": 500,
      "estimatedBytes": 4096000,
      "percentage": 96.15
    }
  ],
  "estimationNote": "Allocation metrics are derived from sampled events (New TLAB allocation and Outside TLAB allocation)."
}
```

---

### 6. `analyze_threads`
- **Purpose**: Summarizes thread execution states, thread counts, and per-thread CPU shares.
- **Parameters**:
  - `recording` *(string, required)*: Recording filename.
- **Example Response**:
```json
{
  "recording": "demo-production.jfr",
  "totalThreadCount": 18,
  "activeThreadCount": 18,
  "peakThreadCount": 24,
  "blockedThreads": 5,
  "waitingThreads": 2,
  "parkedThreads": 4,
  "topCpuThreads": [
    {
      "threadName": "main",
      "cpuSamples": 1780,
      "percentage": 96.22
    }
  ]
}
```

---

### 7. `analyze_locks`
- **Purpose**: Analyzes monitor enter events to detect thread serialization bottlenecks.
- **Parameters**:
  - `recording` *(string, required)*: Recording filename.
  - `topN` *(integer, optional, default: 10)*: Number of contentions to return.
- **Example Response**:
```json
{
  "recording": "demo-production.jfr",
  "totalContentionEvents": 48,
  "totalBlockedDurationMs": 2740.5,
  "contentions": [
    {
      "monitorClass": "java.lang.Object",
      "blockedThreads": 5,
      "eventCount": 48,
      "blockedTimeMs": 2740.5,
      "maxBlockedTimeMs": 150.2,
      "avgBlockedTimeMs": 57.09,
      "contendingThreads": ["InventoryWorker-0", "InventoryWorker-1", "InventoryWorker-2"]
    }
  ]
}
```

---

### 8. `analyze_exceptions`
- **Purpose**: Identifies exception storms, top throwing classes, and fatal JVM errors.
- **Parameters**:
  - `recording` *(string, required)*: Recording filename.
  - `topN` *(integer, optional, default: 10)*: Number of top exceptions to return.
- **Example Response**:
```json
{
  "recording": "demo-production.jfr",
  "totalExceptionsThrown": 15,
  "totalErrors": 0,
  "topExceptions": [
    {
      "exceptionClass": "java.lang.IllegalArgumentException",
      "count": 15,
      "sampleMessage": "Invalid order payload sequence 0",
      "isError": false
    }
  ]
}
```

---

### 9. `analyze_io`
- **Purpose**: Measures File and Socket I/O operation counts, byte volumes, and slow events.
- **Parameters**:
  - `recording` *(string, required)*: Recording filename.
  - `topN` *(integer, optional, default: 10)*: Number of slow events to return.

---

### 10. `analyze_latency`
- **Purpose**: Identifies outlier latency events across monitor waits, GC pauses, and I/O exceeding a threshold.
- **Parameters**:
  - `recording` *(string, required)*: Recording filename.
  - `thresholdMs` *(number, optional, default: 100.0)*: Minimum duration in milliseconds.

---

### 11. `analyze_recording`
- **Purpose**: Primary multi-dimensional diagnostic triage tool. Runs all analyzers, correlates evidence, ranks findings by severity and confidence, reports healthy areas, and proposes prioritized investigation steps.
- **Parameters**:
  - `recording` *(string, required)*: Recording filename.
- **Example Response**:
```json
{
  "recording": "demo-production.jfr",
  "overallAssessment": "HIGH",
  "findings": [
    {
      "category": "CPU",
      "severity": "HIGH",
      "confidence": 0.88,
      "title": "Dominant CPU Hotspot Detected in OrderService.calculatePrice()",
      "summary": "Method OrderService.calculatePrice() represents 96.12% of all execution samples.",
      "evidence": [
        {
          "metric": "methodCpuSamplePercentage",
          "value": 96.12,
          "unit": "percent_samples",
          "source": "jdk.ExecutionSample",
          "confidence": 0.90,
          "description": "Method calculatePrice() accounted for 96.12% of execution samples"
        }
      ],
      "impact": "Significant CPU cycles concentrated in a single method.",
      "recommendedInvestigation": [
        "Review algorithm and loop structures in OrderService.calculatePrice()",
        "Check for redundant computation or missing caching"
      ]
    }
  ],
  "healthyAreas": [
    "GC pause times appear normal (max pause: 9.2ms, total pause: 14.5ms)",
    "No exception storm or JVM fatal error events detected"
  ],
  "nextSteps": [
    "Review algorithm and loop structures in OrderService.calculatePrice()",
    "Inspect stack traces to identify callers leading to this hotspot"
  ]
}
```

---

## Error Handling

When a tool operation fails due to client input or environmental constraints, it returns a structured JSON error response with `isError: true`:

```json
{
  "isError": true,
  "errorCode": "RECORDING_NOT_FOUND",
  "message": "Recording 'production.jfr' was not found in the configured recording directory."
}
```

Common error codes:
- `RECORDING_NOT_FOUND`: Specified `.jfr` file does not exist.
- `RECORDING_ACCESS_DENIED`: Path traversal (`..`) or symlink escape attempted.
- `INVALID_RECORDING`: File is not a `.jfr` file or contains corrupted headers.
- `INVALID_ARGUMENT`: Missing required arguments or invalid numbers.
- `INTERNAL_ERROR`: Unexpected internal exception (details logged to STDERR).
