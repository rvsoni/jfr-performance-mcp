package com.ravi.jfrmcp.jfr;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Cache interface for JFR analysis results.
 */
public interface RecordingAnalysisCache {

    /**
     * Cache key uniquely identifying a recording file version.
     */
    record CacheKey(String absolutePath, long lastModifiedMillis, long fileSize) {
        public static Optional<CacheKey> of(Path path) {
            try {
                if (!Files.exists(path)) {
                    return Optional.empty();
                }
                long lastModified = Files.getLastModifiedTime(path).toMillis();
                long size = Files.size(path);
                return Optional.of(new CacheKey(path.toAbsolutePath().normalize().toString(), lastModified, size));
            } catch (Exception e) {
                return Optional.empty();
            }
        }
    }

    <T> Optional<T> get(CacheKey key, Class<T> type);

    <T> void put(CacheKey key, Class<T> type, T value);

    void invalidate(CacheKey key);

    void clear();
}
