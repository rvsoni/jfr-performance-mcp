package com.ravi.jfrmcp;

import com.ravi.jfrmcp.config.JfrConfiguration;
import com.ravi.jfrmcp.jfr.analyzer.JfrAnalysisEngine;
import com.ravi.jfrmcp.mcp.JfrMcpServer;
import com.ravi.jfrmcp.model.FullAnalysisResult;
import com.ravi.jfrmcp.model.PerformanceFinding;
import com.ravi.jfrmcp.util.PathValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Main application entrypoint for jfr-performance-mcp.
 * Runs as an MCP STDIO server by default, or provides a CLI investigation mode when invoked with --analyze.
 */
public class JfrPerformanceMcpApplication {

    private static final Logger log = LoggerFactory.getLogger(JfrPerformanceMcpApplication.class);

    public static void main(String[] args) {
        if (args.length > 0 && ("--help".equals(args[0]) || "-h".equals(args[0]))) {
            printHelp();
            return;
        }

        if (args.length >= 2 && "--analyze".equals(args[0])) {
            runCliAnalysis(args[1]);
            return;
        }

        runMcpServer();
    }

    private static void printHelp() {
        System.err.println("jfr-performance-mcp - JVM Performance Investigation MCP Server");
        System.err.println("Usage:");
        System.err.println("  java -jar jfr-performance-mcp.jar                     # Starts MCP Server (STDIO transport)");
        System.err.println("  java -jar jfr-performance-mcp.jar --analyze <file>    # Runs standalone CLI diagnostic");
        System.err.println("  java -jar jfr-performance-mcp.jar --help              # Shows this help message");
    }

    private static void runCliAnalysis(String filePath) {
        try {
            Path path = Paths.get(filePath).toAbsolutePath().normalize();
            JfrConfiguration config = new JfrConfiguration();
            JfrAnalysisEngine engine = new JfrAnalysisEngine(config, new com.ravi.jfrmcp.jfr.InMemoryRecordingAnalysisCache());

            FullAnalysisResult result = engine.analyzeRecording(path);

            System.out.println("JFR Performance Analysis");
            System.out.println("========================");
            System.out.println("Recording: " + result.recording());
            if (result.summary() != null && result.summary().durationSeconds() != null) {
                System.out.println("Duration: " + Math.round(result.summary().durationSeconds()) + " seconds");
            }
            System.out.println("Overall Assessment: [" + result.overallAssessment() + "]");
            System.out.println("\nFindings:");
            if (result.findings().isEmpty()) {
                System.out.println("  [INFO] No significant performance anomalies detected.");
            } else {
                for (PerformanceFinding f : result.findings()) {
                    System.out.println("  [" + f.severity() + "] " + f.title());
                    if (f.summary() != null) {
                        System.out.println("         " + f.summary());
                    }
                }
            }

            System.out.println("\nRecommended Investigation:");
            if (result.nextSteps().isEmpty()) {
                System.out.println("  Routine observability monitoring.");
            } else {
                for (String step : result.nextSteps()) {
                    System.out.println("  - " + step);
                }
            }
        } catch (Exception e) {
            System.err.println("CLI Analysis Error: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void runMcpServer() {
        JfrMcpServer server = new JfrMcpServer();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown signal received.");
            server.close();
        }));

        try {
            server.start();
            // Block main thread to keep STDIO process active until client disconnects / stdin closes
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.info("MCP server thread interrupted.");
        } catch (Exception e) {
            log.error("Fatal error starting MCP server", e);
            System.exit(1);
        } finally {
            server.close();
        }
    }
}
