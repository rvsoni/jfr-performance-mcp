package com.ravi.jfrmcp.mcp;

import com.ravi.jfrmcp.config.JfrConfiguration;
import com.ravi.jfrmcp.exception.JfrMcpException;
import com.ravi.jfrmcp.jfr.analyzer.JfrAnalysisEngine;
import com.ravi.jfrmcp.model.LockAnalysisResult;
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
 * MCP Tools for monitor contention and synchronization profiling.
 */
public class LockTools {

    private static final Logger log = LoggerFactory.getLogger(LockTools.class);

    private final JfrConfiguration configuration;
    private final JfrAnalysisEngine analysisEngine;

    public LockTools(JfrConfiguration configuration, JfrAnalysisEngine analysisEngine) {
        this.configuration = configuration;
        this.analysisEngine = analysisEngine;
    }

    public McpServerFeatures.SyncToolSpecification createAnalyzeLocksTool() {
        Map<String, Object> schema = Map.of(
            "type", "object",
            "properties", Map.of(
                "recording", Map.of(
                    "type", "string",
                    "description", "Filename or relative path of the .jfr file within the configured recordings directory."
                ),
                "topN", Map.of(
                    "type", "integer",
                    "description", "Maximum number of contended monitors/locations to return (default 10, max " + configuration.getMaxTopN() + ")."
                )
            ),
            "required", List.of("recording")
        );

        McpSchema.Tool tool = McpSchema.Tool.builder("analyze_locks", schema)
            .description("Analyze Java monitor and synchronization contention in a JFR recording. " +
                "Use this tool when investigating high latency, blocked threads, thread starvation, or suspected synchronized/monitor contention. " +
                "Returns measured evidence including contended monitor classes, blocked thread counts, cumulative wait times, " +
                "and stack traces. Do not infer application-level causality unless supported by JFR evidence.")
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
                    LockAnalysisResult result = analysisEngine.analyzeLocks(path, topN);

                    return McpSchema.CallToolResult.builder()
                        .content(List.of(new McpSchema.TextContent(JsonUtils.toPrettyJson(result))))
                        .build();
                } catch (JfrMcpException e) {
                    log.warn("analyze_locks domain error: {}", e.getMessage());
                    return createErrorResult(e.getErrorCode(), e.getMessage());
                } catch (Exception e) {
                    log.error("Unexpected error in analyze_locks", e);
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
