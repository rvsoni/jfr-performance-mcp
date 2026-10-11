package com.ravi.jfrmcp.mcp;

import com.ravi.jfrmcp.config.JfrConfiguration;
import com.ravi.jfrmcp.jfr.analyzer.JfrAnalysisEngine;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * MCP Server implementation exposing JFR analysis tools over STDIO transport.
 */
public class JfrMcpServer implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(JfrMcpServer.class);
    private static final String SERVER_NAME = "jfr-performance-mcp";

    private final JfrConfiguration configuration;
    private final JfrAnalysisEngine analysisEngine;
    private final String serverVersion;
    private McpSyncServer mcpServer;

    public JfrMcpServer() {
        this(new JfrConfiguration(), new JfrAnalysisEngine());
    }

    public JfrMcpServer(JfrConfiguration configuration, JfrAnalysisEngine analysisEngine) {
        this.configuration = configuration;
        this.analysisEngine = analysisEngine;
        this.serverVersion = resolveVersion();
    }

    private String resolveVersion() {
        try (InputStream is = getClass().getResourceAsStream("/META-INF/maven/com.ravi.jfr/jfr-performance-mcp/pom.properties")) {
            if (is != null) {
                Properties props = new Properties();
                props.load(is);
                String ver = props.getProperty("version");
                if (ver != null && !ver.isBlank()) {
                    return ver.trim();
                }
            }
        } catch (Exception ignored) {}

        Package pkg = getClass().getPackage();
        if (pkg != null && pkg.getImplementationVersion() != null) {
            return pkg.getImplementationVersion();
        }
        return "1.0.0";
    }

    public void start() {
        log.info("Initializing {} v{} (Recordings directory: {})",
            SERVER_NAME, serverVersion, configuration.getRecordingsDirectory());

        RecordingTools recordingTools = new RecordingTools(configuration, analysisEngine);
        CpuTools cpuTools = new CpuTools(configuration, analysisEngine);
        GcTools gcTools = new GcTools(configuration, analysisEngine);
        ThreadTools threadTools = new ThreadTools(configuration, analysisEngine);
        LockTools lockTools = new LockTools(configuration, analysisEngine);
        AnalysisTools analysisTools = new AnalysisTools(configuration, analysisEngine);

        List<McpServerFeatures.SyncToolSpecification> toolSpecs = new ArrayList<>();
        // Recording tools
        toolSpecs.add(recordingTools.createListRecordingsTool());
        toolSpecs.add(recordingTools.createRecordingSummaryTool());
        // CPU tools
        toolSpecs.add(cpuTools.createAnalyzeCpuTool());
        // GC & Memory tools
        toolSpecs.add(gcTools.createAnalyzeGcTool());
        toolSpecs.add(gcTools.createAnalyzeAllocationTool());
        // Thread tools
        toolSpecs.add(threadTools.createAnalyzeThreadsTool());
        // Lock tools
        toolSpecs.add(lockTools.createAnalyzeLocksTool());
        // Exception, I/O, Latency, and Correlated Analysis tools
        toolSpecs.add(analysisTools.createAnalyzeExceptionsTool());
        toolSpecs.add(analysisTools.createAnalyzeIoTool());
        toolSpecs.add(analysisTools.createAnalyzeLatencyTool());
        toolSpecs.add(analysisTools.createAnalyzeRecordingTool());

        StdioServerTransportProvider transportProvider =
            new StdioServerTransportProvider(McpJsonDefaults.getMapper());

        McpSchema.ServerCapabilities capabilities = McpSchema.ServerCapabilities.builder()
            .tools(true)
            .logging()
            .build();

        this.mcpServer = McpServer.sync(transportProvider)
            .serverInfo(SERVER_NAME, serverVersion)
            .capabilities(capabilities)
            .tools(toolSpecs)
            .build();

        log.info("{} started successfully over STDIO with {} registered tools.", SERVER_NAME, toolSpecs.size());
    }

    @Override
    public void close() {
        if (mcpServer != null) {
            log.info("Shutting down {}...", SERVER_NAME);
            try {
                mcpServer.close();
            } catch (Exception e) {
                log.warn("Error during MCP server shutdown: {}", e.getMessage());
            }
        }
    }

    public McpSyncServer getMcpServer() {
        return mcpServer;
    }

    public String getServerVersion() {
        return serverVersion;
    }

    public JfrConfiguration getConfiguration() {
        return configuration;
    }

    public JfrAnalysisEngine getAnalysisEngine() {
        return analysisEngine;
    }
}
