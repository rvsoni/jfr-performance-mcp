package com.ravi.jfrmcp.mcp;

import com.ravi.jfrmcp.config.JfrConfiguration;
import com.ravi.jfrmcp.exception.JfrMcpException;
import com.ravi.jfrmcp.jfr.analyzer.JfrAnalysisEngine;
import com.ravi.jfrmcp.model.ExceptionAnalysisResult;
import com.ravi.jfrmcp.model.FullAnalysisResult;
import com.ravi.jfrmcp.model.IoAnalysisResult;
import com.ravi.jfrmcp.model.LatencyAnalysisResult;
import com.ravi.jfrmcp.util.JsonUtils;
import com.ravi.jfrmcp.util.PathValidator;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * MCP Tools for Exception, I/O, Latency, and comprehensive multi-dimensional investigation.
 */
public class AnalysisTools {

    private static final Logger log = LoggerFactory.getLogger(AnalysisTools.class);

    private final JfrConfiguration configuration;
    private final JfrAnalysisEngine analysisEngine;

    public AnalysisTools(JfrConfiguration configuration, JfrAnalysisEngine analysisEngine) {
        this.configuration = configuration;
        this.analysisEngine = analysisEngine;
    }

    public McpServerFeatures.SyncToolSpecification createAnalyzeExceptionsTool() {
        Map<String, Object> schema = Map.of(
            "type", "object",
            "properties", Map.of(
                "recording", Map.of(
                    "type", "string",
                    "description", "Filename or relative path of the .jfr file within the configured recordings directory."
                ),
                "topN", Map.of(
                    "type", "integer",
                    "description", "Maximum number of top exceptions/methods to return (default 10, max " + configuration.getMaxTopN() + ")."
                )
            ),
            "required", List.of("recording")
        );

        McpSchema.Tool tool = McpSchema.Tool.builder("analyze_exceptions", schema)
            .description("Analyze Java exception and error throw events in a JFR recording. " +
                "Use this tool when investigating elevated error rates, exception storms, or CPU degradation caused by stack trace construction. " +
                "Returns top throwing classes, methods, threads, and distinguishes between thrown exceptions and fatal JVM errors.")
            .build();

        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler((exchange, request) -> {
                try {
                    Map<String, Object> args = request.arguments() != null ? request.arguments() : Collections.emptyMap();
                    String recordingName = (String) args.get("recording");
                    if (recordingName == null || recordingName.isBlank()) {
                        return createErrorResult("INVALID_ARGUMENT", "Argument 'recording' is required.");
                    }

                    int topN = 10;
                    if (args.containsKey("topN") && args.get("topN") instanceof Number n) {
                        topN = n.intValue();
                    }

                    Path path = PathValidator.validateAndResolve(configuration.getRecordingsDirectory(), recordingName);
                    ExceptionAnalysisResult result = analysisEngine.analyzeExceptions(path, topN);

                    return McpSchema.CallToolResult.builder()
                        .content(List.of(new McpSchema.TextContent(JsonUtils.toPrettyJson(result))))
                        .build();
                } catch (JfrMcpException e) {
                    log.warn("analyze_exceptions domain error: {}", e.getMessage());
                    return createErrorResult(e.getErrorCode(), e.getMessage());
                } catch (Exception e) {
                    log.error("Unexpected error in analyze_exceptions", e);
                    return createErrorResult("INTERNAL_ERROR", "Unexpected error: " + e.getMessage());
                }
            })
            .build();
    }

    public McpServerFeatures.SyncToolSpecification createAnalyzeIoTool() {
        Map<String, Object> schema = Map.of(
            "type", "object",
            "properties", Map.of(
                "recording", Map.of(
                    "type", "string",
                    "description", "Filename or relative path of the .jfr file within the configured recordings directory."
                ),
                "topN", Map.of(
                    "type", "integer",
                    "description", "Maximum number of slow I/O events to return (default 10, max " + configuration.getMaxTopN() + ")."
                )
            ),
            "required", List.of("recording")
        );

        McpSchema.Tool tool = McpSchema.Tool.builder("analyze_io", schema)
            .description("Analyze Socket and File I/O operations and latency in a JFR recording. " +
                "Use this tool when investigating slow downstream network responses, database queries, blocked threads during I/O, or disk write delays. " +
                "Returns cumulative read/write durations, byte totals, and slowest individual I/O events.")
            .build();

        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler((exchange, request) -> {
                try {
                    Map<String, Object> args = request.arguments() != null ? request.arguments() : Collections.emptyMap();
                    String recordingName = (String) args.get("recording");
                    if (recordingName == null || recordingName.isBlank()) {
                        return createErrorResult("INVALID_ARGUMENT", "Argument 'recording' is required.");
                    }

                    int topN = 10;
                    if (args.containsKey("topN") && args.get("topN") instanceof Number n) {
                        topN = n.intValue();
                    }

                    Path path = PathValidator.validateAndResolve(configuration.getRecordingsDirectory(), recordingName);
                    IoAnalysisResult result = analysisEngine.analyzeIo(path, topN);

                    return McpSchema.CallToolResult.builder()
                        .content(List.of(new McpSchema.TextContent(JsonUtils.toPrettyJson(result))))
                        .build();
                } catch (JfrMcpException e) {
                    log.warn("analyze_io domain error: {}", e.getMessage());
                    return createErrorResult(e.getErrorCode(), e.getMessage());
                } catch (Exception e) {
                    log.error("Unexpected error in analyze_io", e);
                    return createErrorResult("INTERNAL_ERROR", "Unexpected error: " + e.getMessage());
                }
            })
            .build();
    }

    public McpServerFeatures.SyncToolSpecification createAnalyzeLatencyTool() {
        Map<String, Object> schema = Map.of(
            "type", "object",
            "properties", Map.of(
                "recording", Map.of(
                    "type", "string",
                    "description", "Filename or relative path of the .jfr file within the configured recordings directory."
                ),
                "thresholdMs", Map.of(
                    "type", "number",
                    "description", "Minimum duration in milliseconds for an event to be captured (default 100.0 ms)."
                )
            ),
            "required", List.of("recording")
        );

        McpSchema.Tool tool = McpSchema.Tool.builder("analyze_latency", schema)
            .description("Identify long-running JVM pause, contention, wait, and I/O events exceeding a specified latency threshold (default 100ms) in a JFR recording. " +
                "Use this tool to isolate outlier latency spikes and identify blocking event types and call stacks. " +
                "Note: Measures JVM-level events, not end-to-end application HTTP/RPC latency.")
            .build();

        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler((exchange, request) -> {
                try {
                    Map<String, Object> args = request.arguments() != null ? request.arguments() : Collections.emptyMap();
                    String recordingName = (String) args.get("recording");
                    if (recordingName == null || recordingName.isBlank()) {
                        return createErrorResult("INVALID_ARGUMENT", "Argument 'recording' is required.");
                    }

                    double thresholdMs = 100.0;
                    if (args.containsKey("thresholdMs") && args.get("thresholdMs") instanceof Number n) {
                        thresholdMs = n.doubleValue();
                    }

                    Path path = PathValidator.validateAndResolve(configuration.getRecordingsDirectory(), recordingName);
                    LatencyAnalysisResult result = analysisEngine.analyzeLatency(path, thresholdMs);

                    return McpSchema.CallToolResult.builder()
                        .content(List.of(new McpSchema.TextContent(JsonUtils.toPrettyJson(result))))
                        .build();
                } catch (JfrMcpException e) {
                    log.warn("analyze_latency domain error: {}", e.getMessage());
                    return createErrorResult(e.getErrorCode(), e.getMessage());
                } catch (Exception e) {
                    log.error("Unexpected error in analyze_latency", e);
                    return createErrorResult("INTERNAL_ERROR", "Unexpected error: " + e.getMessage());
                }
            })
            .build();
    }

    public McpServerFeatures.SyncToolSpecification createAnalyzeRecordingTool() {
        Map<String, Object> schema = Map.of(
            "type", "object",
            "properties", Map.of(
                "recording", Map.of(
                    "type", "string",
                    "description", "Filename or relative path of the .jfr file within the configured recordings directory."
                )
            ),
            "required", List.of("recording")
        );

        McpSchema.Tool tool = McpSchema.Tool.builder("analyze_recording", schema)
            .description("Perform a comprehensive, correlated multi-dimensional performance investigation of a JFR recording. " +
                "Executes recording summary, CPU, GC, memory allocation, thread concurrency, lock contention, exception, I/O, and latency analyses. " +
                "Correlates JVM evidence into prioritized findings with confidence scores, healthy areas, and recommended next steps. " +
                "This is the most important tool for initial AI performance triage and diagnosis.")
            .build();

        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler((exchange, request) -> {
                try {
                    Map<String, Object> args = request.arguments() != null ? request.arguments() : Collections.emptyMap();
                    String recordingName = (String) args.get("recording");
                    if (recordingName == null || recordingName.isBlank()) {
                        return createErrorResult("INVALID_ARGUMENT", "Argument 'recording' is required.");
                    }

                    Path path = PathValidator.validateAndResolve(configuration.getRecordingsDirectory(), recordingName);
                    FullAnalysisResult result = analysisEngine.analyzeRecording(path);

                    return McpSchema.CallToolResult.builder()
                        .content(List.of(new McpSchema.TextContent(JsonUtils.toPrettyJson(result))))
                        .build();
                } catch (JfrMcpException e) {
                    log.warn("analyze_recording domain error: {}", e.getMessage());
                    return createErrorResult(e.getErrorCode(), e.getMessage());
                } catch (Exception e) {
                    log.error("Unexpected error in analyze_recording", e);
                    return createErrorResult("INTERNAL_ERROR", "Unexpected error: " + e.getMessage());
                }
            })
            .build();
    }

    private McpSchema.CallToolResult createErrorResult(String errorCode, String message) {
        ToolErrorResponse err = ToolErrorResponse.of(errorCode, message);
        return McpSchema.CallToolResult.builder()
            .isError(true)
            .content(List.of(new McpSchema.TextContent(JsonUtils.toJson(err))))
            .build();
    }
}
