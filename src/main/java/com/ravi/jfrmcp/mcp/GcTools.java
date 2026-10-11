package com.ravi.jfrmcp.mcp;

import com.ravi.jfrmcp.config.JfrConfiguration;
import com.ravi.jfrmcp.exception.JfrMcpException;
import com.ravi.jfrmcp.jfr.analyzer.JfrAnalysisEngine;
import com.ravi.jfrmcp.model.AllocationAnalysisResult;
import com.ravi.jfrmcp.model.GcAnalysisResult;
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
 * MCP Tools for Garbage Collection and memory allocation profiling.
 */
public class GcTools {

    private static final Logger log = LoggerFactory.getLogger(GcTools.class);

    private final JfrConfiguration configuration;
    private final JfrAnalysisEngine analysisEngine;

    public GcTools(JfrConfiguration configuration, JfrAnalysisEngine analysisEngine) {
        this.configuration = configuration;
        this.analysisEngine = analysisEngine;
    }

    public McpServerFeatures.SyncToolSpecification createAnalyzeGcTool() {
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

        McpSchema.Tool tool = McpSchema.Tool.builder("analyze_gc", schema)
            .description("Analyze Garbage Collection events, pause durations, collection frequencies, and heap occupancy in a JFR recording. " +
                "Use this tool when investigating latency spikes, stop-the-world pauses, memory exhaustion, or GC pressure. " +
                "Returns structured metrics including GC counts, max/average pause times, young vs old collections, and rule-based severity assessments.")
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
                    GcAnalysisResult result = analysisEngine.analyzeGc(path);

                    return McpSchema.CallToolResult.builder()
                        .content(List.of(new McpSchema.TextContent(JsonUtils.toPrettyJson(result))))
                        .build();
                } catch (JfrMcpException e) {
                    log.warn("analyze_gc domain error: {}", e.getMessage());
                    return createErrorResult(e.getErrorCode(), e.getMessage());
                } catch (Exception e) {
                    log.error("Unexpected error in analyze_gc", e);
                    return createErrorResult("INTERNAL_ERROR", "Unexpected error: " + e.getMessage());
                }
            })
            .build();
    }

    public McpServerFeatures.SyncToolSpecification createAnalyzeAllocationTool() {
        Map<String, Object> schema = Map.of(
            "type", "object",
            "properties", Map.of(
                "recording", Map.of(
                    "type", "string",
                    "description", "Filename or relative path of the .jfr file within the configured recordings directory."
                ),
                "topN", Map.of(
                    "type", "integer",
                    "description", "Maximum number of allocating classes/threads to return (default 10, max " + configuration.getMaxTopN() + ")."
                )
            ),
            "required", List.of("recording")
        );

        McpSchema.Tool tool = McpSchema.Tool.builder("analyze_allocation", schema)
            .description("Analyze object allocation pressure, top allocating classes, estimated allocation rates, and thread allocation hotspots in a JFR recording. " +
                "Use this tool when investigating high memory turnover, premature promotion, or rapid young generation exhaustion. " +
                "Note: Metrics are derived from sampled TLAB allocations; values represent estimated relative distribution rather than exact totals.")
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
                    AllocationAnalysisResult result = analysisEngine.analyzeAllocation(path, topN);

                    return McpSchema.CallToolResult.builder()
                        .content(List.of(new McpSchema.TextContent(JsonUtils.toPrettyJson(result))))
                        .build();
                } catch (JfrMcpException e) {
                    log.warn("analyze_allocation domain error: {}", e.getMessage());
                    return createErrorResult(e.getErrorCode(), e.getMessage());
                } catch (Exception e) {
                    log.error("Unexpected error in analyze_allocation", e);
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
