package com.ravi.jfrmcp.mcp;

import com.ravi.jfrmcp.config.JfrConfiguration;
import com.ravi.jfrmcp.exception.JfrMcpException;
import com.ravi.jfrmcp.jfr.analyzer.JfrAnalysisEngine;
import com.ravi.jfrmcp.model.ThreadAnalysisResult;
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
 * MCP Tools for thread state, count, and concurrency profiling.
 */
public class ThreadTools {

    private static final Logger log = LoggerFactory.getLogger(ThreadTools.class);

    private final JfrConfiguration configuration;
    private final JfrAnalysisEngine analysisEngine;

    public ThreadTools(JfrConfiguration configuration, JfrAnalysisEngine analysisEngine) {
        this.configuration = configuration;
        this.analysisEngine = analysisEngine;
    }

    public McpServerFeatures.SyncToolSpecification createAnalyzeThreadsTool() {
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

        McpSchema.Tool tool = McpSchema.Tool.builder("analyze_threads", schema)
            .description("Analyze Java thread concurrency, thread counts, thread execution states (RUNNABLE, BLOCKED, WAITING, PARKED), " +
                "and thread CPU activity in a JFR recording. Use this tool when investigating thread pool exhaustion, thread leaks, " +
                "cascading delays, or uneven concurrency distribution.")
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
                    ThreadAnalysisResult result = analysisEngine.analyzeThreads(path);

                    return McpSchema.CallToolResult.builder()
                        .content(List.of(new McpSchema.TextContent(JsonUtils.toPrettyJson(result))))
                        .build();
                } catch (JfrMcpException e) {
                    log.warn("analyze_threads domain error: {}", e.getMessage());
                    return createErrorResult(e.getErrorCode(), e.getMessage());
                } catch (Exception e) {
                    log.error("Unexpected error in analyze_threads", e);
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
