package com.ravi.jfrmcp.config;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

/**
 * Configuration options for the JFR Performance MCP Server.
 * Supports configuration via system properties and environment variables.
 */
public class JfrConfiguration {

    private final Path recordingsDirectory;
    private final int maxTopN;
    private final boolean cacheEnabled;
    private final long maxFileSizeBytes;
    private final Duration analysisTimeout;

    public JfrConfiguration() {
        this(
            resolveRecordingsDirectory(),
            resolveMaxTopN(),
            resolveCacheEnabled(),
            resolveMaxFileSize(),
            resolveTimeout()
        );
    }

    public JfrConfiguration(Path recordingsDirectory, int maxTopN, boolean cacheEnabled, long maxFileSizeBytes, Duration analysisTimeout) {
        this.recordingsDirectory = recordingsDirectory.toAbsolutePath().normalize();
        this.maxTopN = maxTopN;
        this.cacheEnabled = cacheEnabled;
        this.maxFileSizeBytes = maxFileSizeBytes;
        this.analysisTimeout = analysisTimeout;
    }

    private static Path resolveRecordingsDirectory() {
        String env = System.getenv("JFR_RECORDINGS_DIR");
        if (env != null && !env.isBlank()) {
            return Paths.get(env.trim());
        }
        String prop = System.getProperty("jfr.recordings.directory");
        if (prop != null && !prop.isBlank()) {
            return Paths.get(prop.trim());
        }
        return Paths.get("./recordings");
    }

    private static int resolveMaxTopN() {
        String env = System.getenv("JFR_MAX_TOP_N");
        if (env != null && !env.isBlank()) {
            try {
                return Integer.parseInt(env.trim());
            } catch (NumberFormatException ignored) {}
        }
        String prop = System.getProperty("jfr.analysis.max-top-n");
        if (prop != null && !prop.isBlank()) {
            try {
                return Integer.parseInt(prop.trim());
            } catch (NumberFormatException ignored) {}
        }
        return 100;
    }

    private static boolean resolveCacheEnabled() {
        String env = System.getenv("JFR_CACHE_ENABLED");
        if (env != null && !env.isBlank()) {
            return Boolean.parseBoolean(env.trim());
        }
        String prop = System.getProperty("jfr.analysis.cache-enabled");
        if (prop != null && !prop.isBlank()) {
            return Boolean.parseBoolean(prop.trim());
        }
        return true;
    }

    private static long resolveMaxFileSize() {
        String env = System.getenv("JFR_MAX_FILE_SIZE");
        if (env != null && !env.isBlank()) {
            try {
                return Long.parseLong(env.trim());
            } catch (NumberFormatException ignored) {}
        }
        String prop = System.getProperty("jfr.analysis.max-file-size");
        if (prop != null && !prop.isBlank()) {
            try {
                return Long.parseLong(prop.trim());
            } catch (NumberFormatException ignored) {}
        }
        return 2L * 1024 * 1024 * 1024; // 2 GB default max
    }

    private static Duration resolveTimeout() {
        String env = System.getenv("JFR_ANALYSIS_TIMEOUT");
        if (env != null && !env.isBlank()) {
            try {
                return Duration.ofSeconds(Long.parseLong(env.trim()));
            } catch (NumberFormatException ignored) {}
        }
        String prop = System.getProperty("jfr.analysis.timeout");
        if (prop != null && !prop.isBlank()) {
            try {
                return Duration.ofSeconds(Long.parseLong(prop.trim()));
            } catch (NumberFormatException ignored) {}
        }
        return Duration.ofSeconds(60);
    }

    public Path getRecordingsDirectory() {
        return recordingsDirectory;
    }

    public int getMaxTopN() {
        return maxTopN;
    }

    public boolean isCacheEnabled() {
        return cacheEnabled;
    }

    public long getMaxFileSizeBytes() {
        return maxFileSizeBytes;
    }

    public Duration getAnalysisTimeout() {
        return analysisTimeout;
    }
}
