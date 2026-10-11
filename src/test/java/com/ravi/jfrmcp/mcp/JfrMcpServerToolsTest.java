package com.ravi.jfrmcp.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.ravi.jfrmcp.TestRecordingFactory;
import com.ravi.jfrmcp.config.JfrConfiguration;
import com.ravi.jfrmcp.jfr.InMemoryRecordingAnalysisCache;
import com.ravi.jfrmcp.jfr.analyzer.JfrAnalysisEngine;
import com.ravi.jfrmcp.util.JsonUtils;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JfrMcpServerToolsTest {

    @TempDir
    static Path tempDir;

    private static Path recordingsDir;
    private static Path sampleRecording;
    private static JfrConfiguration configuration;
    private static JfrAnalysisEngine analysisEngine;

    private static RecordingTools recordingTools;
    private static CpuTools cpuTools;
    private static GcTools gcTools;
    private static ThreadTools threadTools;
    private static LockTools lockTools;
    private static AnalysisTools analysisTools;

    @BeforeAll
    static void setUpAll() throws Exception {
        recordingsDir = tempDir.resolve("recordings");
        Files.createDirectories(recordingsDir);
        sampleRecording = TestRecordingFactory.createRealisticRecording(recordingsDir.resolve("demo.jfr"));

        configuration = new JfrConfiguration(recordingsDir, 100, true, 2L * 1024 * 1024 * 1024, Duration.ofSeconds(30));
        analysisEngine = new JfrAnalysisEngine(configuration, new InMemoryRecordingAnalysisCache());

        recordingTools = new RecordingTools(configuration, analysisEngine);
        cpuTools = new CpuTools(configuration, analysisEngine);
        gcTools = new GcTools(configuration, analysisEngine);
        threadTools = new ThreadTools(configuration, analysisEngine);
        lockTools = new LockTools(configuration, analysisEngine);
        analysisTools = new AnalysisTools(configuration, analysisEngine);
    }

    private McpSchema.CallToolResult executeTool(McpServerFeatures.SyncToolSpecification toolSpec, Map<String, Object> arguments) {
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(toolSpec.tool().name(), arguments);
        return toolSpec.callHandler().apply(null, request);
    }

    private String getResultText(McpSchema.CallToolResult result) {
        assertThat(result.content()).isNotEmpty();
        assertThat(result.content().get(0)).isInstanceOf(McpSchema.TextContent.class);
        return ((McpSchema.TextContent) result.content().get(0)).text();
    }

    @Test
    void shouldListRecordings() throws Exception {
        McpServerFeatures.SyncToolSpecification tool = recordingTools.createListRecordingsTool();
        McpSchema.CallToolResult result = executeTool(tool, Map.of());

        assertThat(Boolean.TRUE.equals(result.isError())).isFalse();
        String json = getResultText(result);
        JsonNode node = JsonUtils.getMapper().readTree(json);
        assertThat(node.has("recordings")).isTrue();
        assertThat(node.get("recordings").isArray()).isTrue();
        assertThat(node.get("recordings").size()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void shouldGetRecordingSummary() throws Exception {
        McpServerFeatures.SyncToolSpecification tool = recordingTools.createRecordingSummaryTool();
        McpSchema.CallToolResult result = executeTool(tool, Map.of("recording", "demo.jfr"));

        assertThat(Boolean.TRUE.equals(result.isError())).isFalse();
        String json = getResultText(result);
        JsonNode node = JsonUtils.getMapper().readTree(json);
        assertThat(node.get("fileName").asText()).isEqualTo("demo.jfr");
        assertThat(node.get("eventCount").asLong()).isGreaterThan(0);
    }

    @Test
    void shouldAnalyzeCpu() throws Exception {
        McpServerFeatures.SyncToolSpecification tool = cpuTools.createAnalyzeCpuTool();
        McpSchema.CallToolResult result = executeTool(tool, Map.of("recording", "demo.jfr", "topN", 5));

        assertThat(Boolean.TRUE.equals(result.isError())).isFalse();
        String json = getResultText(result);
        JsonNode node = JsonUtils.getMapper().readTree(json);
        assertThat(node.get("recording").asText()).isEqualTo("demo.jfr");
        assertThat(node.has("topMethods")).isTrue();
        assertThat(node.get("topMethods").size()).isGreaterThan(0);
    }

    @Test
    void shouldAnalyzeGc() throws Exception {
        McpServerFeatures.SyncToolSpecification tool = gcTools.createAnalyzeGcTool();
        McpSchema.CallToolResult result = executeTool(tool, Map.of("recording", "demo.jfr"));

        assertThat(Boolean.TRUE.equals(result.isError())).isFalse();
        String json = getResultText(result);
        JsonNode node = JsonUtils.getMapper().readTree(json);
        assertThat(node.has("gcCount")).isTrue();
        assertThat(node.has("assessment")).isTrue();
    }

    @Test
    void shouldAnalyzeAllocation() throws Exception {
        McpServerFeatures.SyncToolSpecification tool = gcTools.createAnalyzeAllocationTool();
        McpSchema.CallToolResult result = executeTool(tool, Map.of("recording", "demo.jfr", "topN", 5));

        assertThat(Boolean.TRUE.equals(result.isError())).isFalse();
        String json = getResultText(result);
        JsonNode node = JsonUtils.getMapper().readTree(json);
        assertThat(node.has("totalAllocationSamples")).isTrue();
        assertThat(node.has("topAllocatingClasses")).isTrue();
    }

    @Test
    void shouldAnalyzeThreads() throws Exception {
        McpServerFeatures.SyncToolSpecification tool = threadTools.createAnalyzeThreadsTool();
        McpSchema.CallToolResult result = executeTool(tool, Map.of("recording", "demo.jfr"));

        assertThat(Boolean.TRUE.equals(result.isError())).isFalse();
        String json = getResultText(result);
        JsonNode node = JsonUtils.getMapper().readTree(json);
        assertThat(node.get("totalThreadCount").asInt()).isGreaterThan(0);
    }

    @Test
    void shouldAnalyzeLocks() throws Exception {
        McpServerFeatures.SyncToolSpecification tool = lockTools.createAnalyzeLocksTool();
        McpSchema.CallToolResult result = executeTool(tool, Map.of("recording", "demo.jfr", "topN", 5));

        assertThat(Boolean.TRUE.equals(result.isError())).isFalse();
        String json = getResultText(result);
        JsonNode node = JsonUtils.getMapper().readTree(json);
        assertThat(node.has("contentions")).isTrue();
    }

    @Test
    void shouldAnalyzeExceptions() throws Exception {
        McpServerFeatures.SyncToolSpecification tool = analysisTools.createAnalyzeExceptionsTool();
        McpSchema.CallToolResult result = executeTool(tool, Map.of("recording", "demo.jfr", "topN", 5));

        assertThat(Boolean.TRUE.equals(result.isError())).isFalse();
        String json = getResultText(result);
        JsonNode node = JsonUtils.getMapper().readTree(json);
        assertThat(node.has("totalExceptionsThrown")).isTrue();
    }

    @Test
    void shouldAnalyzeIo() throws Exception {
        McpServerFeatures.SyncToolSpecification tool = analysisTools.createAnalyzeIoTool();
        McpSchema.CallToolResult result = executeTool(tool, Map.of("recording", "demo.jfr", "topN", 5));

        assertThat(Boolean.TRUE.equals(result.isError())).isFalse();
        String json = getResultText(result);
        JsonNode node = JsonUtils.getMapper().readTree(json);
        assertThat(node.has("fileReads")).isTrue();
    }

    @Test
    void shouldAnalyzeLatency() throws Exception {
        McpServerFeatures.SyncToolSpecification tool = analysisTools.createAnalyzeLatencyTool();
        McpSchema.CallToolResult result = executeTool(tool, Map.of("recording", "demo.jfr", "thresholdMs", 5.0));

        assertThat(Boolean.TRUE.equals(result.isError())).isFalse();
        String json = getResultText(result);
        JsonNode node = JsonUtils.getMapper().readTree(json);
        assertThat(node.has("thresholdMs")).isTrue();
    }

    @Test
    void shouldPerformCorrelatedAnalyzeRecording() throws Exception {
        McpServerFeatures.SyncToolSpecification tool = analysisTools.createAnalyzeRecordingTool();
        McpSchema.CallToolResult result = executeTool(tool, Map.of("recording", "demo.jfr"));

        assertThat(Boolean.TRUE.equals(result.isError())).isFalse();
        String json = getResultText(result);
        JsonNode node = JsonUtils.getMapper().readTree(json);
        assertThat(node.get("recording").asText()).isEqualTo("demo.jfr");
        assertThat(node.has("overallAssessment")).isTrue();
        assertThat(node.has("findings")).isTrue();
        assertThat(node.has("healthyAreas")).isTrue();
        assertThat(node.has("nextSteps")).isTrue();
    }

    @Test
    void shouldRejectPathTraversalWithStructuredError() throws Exception {
        McpServerFeatures.SyncToolSpecification tool = recordingTools.createRecordingSummaryTool();
        McpSchema.CallToolResult result = executeTool(tool, Map.of("recording", "../../../../etc/passwd"));

        assertThat(result.isError()).isTrue();
        String json = getResultText(result);
        JsonNode node = JsonUtils.getMapper().readTree(json);
        assertThat(node.get("isError").asBoolean()).isTrue();
        assertThat(node.get("errorCode").asText()).isEqualTo("RECORDING_ACCESS_DENIED");
        assertThat(node.get("message").asText()).contains("Path traversal detected");
    }

    @Test
    void shouldReturnStructuredErrorForMissingRecording() throws Exception {
        McpServerFeatures.SyncToolSpecification tool = recordingTools.createRecordingSummaryTool();
        McpSchema.CallToolResult result = executeTool(tool, Map.of("recording", "non-existent.jfr"));

        assertThat(result.isError()).isTrue();
        String json = getResultText(result);
        JsonNode node = JsonUtils.getMapper().readTree(json);
        assertThat(node.get("isError").asBoolean()).isTrue();
        assertThat(node.get("errorCode").asText()).isEqualTo("RECORDING_NOT_FOUND");
    }

    @Test
    void shouldReturnStructuredErrorForMissingArgument() throws Exception {
        McpServerFeatures.SyncToolSpecification tool = recordingTools.createRecordingSummaryTool();
        McpSchema.CallToolResult result = executeTool(tool, Map.of());

        assertThat(result.isError()).isTrue();
        String json = getResultText(result);
        JsonNode node = JsonUtils.getMapper().readTree(json);
        assertThat(node.get("isError").asBoolean()).isTrue();
        assertThat(node.get("errorCode").asText()).isEqualTo("INVALID_ARGUMENT");
    }
}
