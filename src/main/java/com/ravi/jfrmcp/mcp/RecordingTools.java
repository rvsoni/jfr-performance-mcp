package com.ravi.jfrmcp.mcp;

import com.ravi.jfrmcp.config.JfrConfiguration;
import com.ravi.jfrmcp.exception.JfrMcpException;
import com.ravi.jfrmcp.jfr.analyzer.JfrAnalysisEngine;
import com.ravi.jfrmcp.model.RecordingSummary;
import com.ravi.jfrmcp.util.JsonUtils;
import com.ravi.jfrmcp.util.PathValidator;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * MCP Tools for recording discovery and basic summary inspection.
 */
public class RecordingTools {

    private static final Logger log = LoggerFactory.getLogger(RecordingTools.class);

    private final JfrConfiguration configuration;
    private final JfrAnalysisEngine analysisEngine;

    public RecordingTools(JfrConfiguration configuration, JfrAnalysisEngine analysisEngine) {
        this.configuration = configuration;
        this.analysisEngine = analysisEngine;
    }

    public record RecordingFileInfo(
        String fileName,
        String path,
        long sizeBytes,
        String lastModified,
        boolean readable
    ) {}

    public McpServerFeatures.SyncToolSpecification createListRecordingsTool() {
        Map<String, Object> schema = Map.of(
            "type", "object",
            "properties", Map.of(
                "directory", Map.of(
                    "type", "string",
                    "description", "Optional relative directory within the configured recordings root to search."
                )
            )
        );

        McpSchema.Tool tool = McpSchema.Tool.builder("list_recordings", schema)
            .description("List available .jfr files from the configured recording directory. " +
                "Use this tool first to discover available Java Flight Recorder files before performing detailed analysis.")
            .build();

        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler((exchange, request) -> {
                try {
                    Map<String, Object> args = request.arguments() != null ? request.arguments() : Collections.emptyMap();
                    String reqDir = (String) args.get("directory");

                    Path baseDir = configuration.getRecordingsDirectory();
                    Path targetDir = PathValidator.validateDirectory(baseDir, reqDir);

                    List<RecordingFileInfo> files = new ArrayList<>();
                    if (Files.exists(targetDir)) {
                        try (Stream<Path> stream = Files.list(targetDir)) {
                            stream.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().toLowerCase().endsWith(".jfr"))
                                .forEach(p -> {
                                    try {
                                        long size = Files.size(p);
                                        Instant lm = Files.getLastModifiedTime(p).toInstant();
                                        boolean readable = Files.isReadable(p);
                                        files.add(new RecordingFileInfo(
                                            p.getFileName().toString(),
                                            baseDir.relativize(p).toString(),
                                            size,
                                            lm.toString(),
                                            readable
                                        ));
                                    } catch (IOException e) {
                                        log.warn("Could not read file attributes for {}: {}", p, e.getMessage());
                                    }
                                });
                        }
                    }

                    Map<String, Object> result = Map.of("recordings", files);
                    return McpSchema.CallToolResult.builder()
                        .content(List.of(new McpSchema.TextContent(JsonUtils.toPrettyJson(result))))
                        .build();
                } catch (JfrMcpException e) {
                    log.warn("list_recordings domain error: {}", e.getMessage());
                    return createErrorResult(e.getErrorCode(), e.getMessage());
                } catch (Exception e) {
                    log.error("Unexpected error in list_recordings", e);
                    return createErrorResult("INTERNAL_ERROR", "Unexpected error: " + e.getMessage());
                }
            })
            .build();
    }

    public McpServerFeatures.SyncToolSpecification createRecordingSummaryTool() {
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

        McpSchema.Tool tool = McpSchema.Tool.builder("recording_summary", schema)
            .description("Inspect JFR recording metadata, JVM version, OS details, recording start/end timestamps, " +
                "duration, and total captured event counts. Use this tool at the beginning of an investigation " +
                "to understand the JVM environment, recording window, and event profile.")
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
                    RecordingSummary summary = analysisEngine.getSummary(path);

                    return McpSchema.CallToolResult.builder()
                        .content(List.of(new McpSchema.TextContent(JsonUtils.toPrettyJson(summary))))
                        .build();
                } catch (JfrMcpException e) {
                    log.warn("recording_summary domain error: {}", e.getMessage());
                    return createErrorResult(e.getErrorCode(), e.getMessage());
                } catch (Exception e) {
                    log.error("Unexpected error in recording_summary", e);
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
