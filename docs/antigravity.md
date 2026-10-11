# Antigravity IDE Integration Guide

This guide describes how to configure and run the `jfr-performance-mcp` server inside **Google Antigravity** (or any MCP-compliant environment like Claude Desktop, Cursor, or VS Code).

---

## 1. Prerequisites

- Java 21 or newer installed (`java -version`).
- Completed build of `jfr-performance-mcp`.

---

## 2. Step 1: Build the Standalone JAR

From the repository root, build the executable shaded JAR:
```bash
./scripts/build.sh
```
The output JAR will be created at:
```
target/jfr-performance-mcp-1.0.0.jar
```

---

## 3. Step 2: Configure Antigravity MCP Settings

Open your Antigravity MCP configuration file (typically in `~/.gemini/antigravity-ide/mcp_config.json` or your workspace `.agents/mcp_config.json`).

Add the `jfr-performance` server definition under `mcpServers`:

```json
{
  "mcpServers": {
    "jfr-performance": {
      "command": "java",
      "args": [
        "-jar",
        "/ABSOLUTE/PATH/TO/target/jfr-performance-mcp-1.0.0.jar"
      ],
      "env": {
        "JFR_RECORDINGS_DIR": "/ABSOLUTE/PATH/TO/recordings"
      }
    }
  }
}
```

> **NOTE:** Replace `/ABSOLUTE/PATH/TO/...` with the absolute paths on your local machine.

---

## 4. Step 3: Verify the Connection

1. Start or restart Antigravity.
2. In the AI Assistant prompt or MCP tool palette, verify that the `jfr-performance` server is active.
3. You should see 11 available tools:
   - `list_recordings`
   - `recording_summary`
   - `analyze_cpu`
   - `analyze_gc`
   - `analyze_allocation`
   - `analyze_threads`
   - `analyze_locks`
   - `analyze_exceptions`
   - `analyze_io`
   - `analyze_latency`
   - `analyze_recording`

---

## 5. Step 4: Run an AI Performance Investigation

Ask Antigravity to analyze a recording in your configured directory:

### Initial Discovery:
> *"What JFR recordings do you have available?"*

Antigravity invokes `list_recordings` and presents the available recordings.

### High-Level Investigation:
> *"Analyze `demo-production.jfr` and tell me what is causing the application degradation."*

Antigravity invokes `analyze_recording` and receives structured facts (CPU sample percentages, lock contention durations, GC pause times, and healthy areas).

### Specialized Follow-Up:
> *"Which methods are consuming the most CPU samples in `demo-production.jfr`?"*

Antigravity calls `analyze_cpu` with `topN: 5` and points to the exact hotspot classes and methods with sample stack traces.

---

## 6. Troubleshooting

### Server Fails to Start
- Verify that Java 21+ is available on your default `PATH`.
- Verify that the path to `jfr-performance-mcp-1.0.0.jar` is an absolute path and the file exists.
- Check standard error logs; diagnostic messages are printed to `STDERR` without polluting the MCP protocol stream.

### Path Traversal / Access Denied
- Ensure target `.jfr` recordings are located inside the directory specified by `JFR_RECORDINGS_DIR`.
- Relative path escapes (`../`) are blocked by design.
