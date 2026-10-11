# Development Guide: jfr-performance-mcp

This guide explains how to build, test, run, and develop `jfr-performance-mcp`.

---

## Prerequisites

- **Java Development Kit**: Java 21 or newer (tested on Java 25 Temurin LTS).
- **Apache Maven**: Version 3.9+ installed and on `PATH`.
- **Git**: For source version control.

Verify your environment:
```bash
java -version
mvn -version
```

---

## Building the Project

The server builds as an executable fat JAR with all runtime dependencies (MCP Java SDK, Jackson, Logback, and ASM) shaded into a standalone artifact.

Run the build script:
```bash
./scripts/build.sh
```

Or execute Maven directly:
```bash
mvn clean package
```

The resulting artifact is located at:
```
target/jfr-performance-mcp-1.0.0.jar
```

---

## Running Automated Tests

The project includes unit tests for every analyzer, the streaming reader, path security validator, and integration tests for all 11 MCP tools:

```bash
mvn clean test
```

### Programmatic Test Recording Generator

Tests do not commit large binary JFR files into Git. Instead, `TestRecordingFactory` programmatically synthesizes representative recordings on demand using JDK Flight Recorder APIs:

```java
// Generates a controlled incident with CPU hotspots, monitor contention, GC, and exceptions:
Path testFile = TestRecordingFactory.createRealisticRecording(tempPath);
```

To generate a sample fixture in `recordings/demo-production.jfr` for manual inspection:
```bash
mvn test-compile
java -cp target/test-classes:target/classes:$(mvn dependency:build-classpath | grep -v '\[INFO\]' | tr '\n' ':') com.ravi.jfrmcp.TestRecordingFactory
```

---

## Running the Server

### Mode 1: MCP STDIO Server (Default)

Used by AI clients (such as Antigravity IDE, Claude Desktop, or Cursor):
```bash
./scripts/run.sh
```
Or directly:
```bash
java -jar target/jfr-performance-mcp-1.0.0.jar
```

> **CRITICAL**: The MCP STDIO transport reserves `STDOUT` exclusively for JSON-RPC protocol messages. Application diagnostic logs are directed strictly to `STDERR`.

### Mode 2: Standalone CLI Diagnostic

For local development and testing without an MCP client:
```bash
./scripts/run.sh --analyze recordings/demo-production.jfr
```
Or directly:
```bash
java -jar target/jfr-performance-mcp-1.0.0.jar --analyze recordings/demo-production.jfr
```

Example CLI Output:
```text
JFR Performance Analysis
========================
Recording: demo-production.jfr
Duration: 1 seconds
Overall Assessment: [HIGH]

Findings:
  [HIGH] Dominant CPU Hotspot Detected in com.ravi.jfrmcp.TestRecordingFactory$OrderService.calculatePrice()
         Method com.ravi.jfrmcp.TestRecordingFactory$OrderService.calculatePrice() represents 96.12% of all execution samples.
  [MEDIUM] Severe Lock Contention on java.lang.Object
         5 threads experienced 2.7s cumulative blocked time contending for java.lang.Object.

Recommended Investigation:
  - Review algorithm and loop structures in com.ravi.jfrmcp.TestRecordingFactory$OrderService.calculatePrice()
  - Check for redundant computation or missing caching in com.ravi.jfrmcp.TestRecordingFactory$OrderService.calculatePrice()
  - Inspect stack traces to identify calling callers leading to this hotspot
```

---

## Configuration Properties

Configuration can be provided via environment variables or Java system properties:

| Property | Environment Variable | Default | Description |
|----------|----------------------|---------|-------------|
| `jfr.recordings.directory` | `JFR_RECORDINGS_DIR` | `./recordings` | Allowed base directory for `.jfr` recordings |
| `jfr.analysis.max-top-n` | `JFR_MAX_TOP_N` | `100` | Upper limit for top-N ranking arguments |
| `jfr.analysis.cache-enabled` | `JFR_CACHE_ENABLED` | `true` | Enables in-memory LRU result caching |
| `jfr.analysis.max-file-size` | `JFR_MAX_FILE_SIZE` | `2147483648` (2GB) | Maximum allowed `.jfr` file size |
| `jfr.analysis.timeout` | `JFR_ANALYSIS_TIMEOUT` | `60` (seconds) | Analysis timeout |

---

## Coding and Architectural Conventions

1. **Layer Independence**:
   - The analysis engine (`com.ravi.jfrmcp.jfr.*`) and domain model (`com.ravi.jfrmcp.model.*`) must never import MCP SDK classes.
2. **Immutable DTOs**:
   - Use Java records for domain results and evidence items.
3. **No STDOUT Logging**:
   - Always log through SLF4J (`LoggerFactory.getLogger(...)`). Never invoke `System.out.println` from server or analyzer classes.
4. **Streaming Processing**:
   - Iterate events via `JfrRecordingReader.forEachEvent(filter, consumer)` instead of loading all events into memory.
