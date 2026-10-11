package com.ravi.jfrmcp.jfr;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory thread-safe LRU cache implementation for recording analysis results.
 */
public class InMemoryRecordingAnalysisCache implements RecordingAnalysisCache {

    private static final int DEFAULT_MAX_ENTRIES = 50;

    private final Map<String, CacheEntry<?>> cache;

    public InMemoryRecordingAnalysisCache() {
        this(DEFAULT_MAX_ENTRIES);
    }

    public InMemoryRecordingAnalysisCache(int maxEntries) {
        this.cache = Collections.synchronizedMap(new LinkedHashMap<String, CacheEntry<?>>(maxEntries, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CacheEntry<?>> eldest) {
                return size() > maxEntries;
            }
        });
    }

    private String buildCompositeKey(CacheKey key, Class<?> type) {
        return key.absolutePath() + ":" + key.lastModifiedMillis() + ":" + key.fileSize() + ":" + type.getName();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(CacheKey key, Class<T> type) {
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        String compositeKey = buildCompositeKey(key, type);
        CacheEntry<?> entry = cache.get(compositeKey);
        if (entry != null && type.isInstance(entry.value())) {
            return Optional.of((T) entry.value());
        }
        return Optional.empty();
    }

    @Override
    public <T> void put(CacheKey key, Class<T> type, T value) {
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        if (value == null) {
            return;
        }
        String compositeKey = buildCompositeKey(key, type);
        cache.put(compositeKey, new CacheEntry<>(value));
    }

    @Override
    public void invalidate(CacheKey key) {
        Objects.requireNonNull(key, "key cannot be null");
        String prefix = key.absolutePath() + ":" + key.lastModifiedMillis() + ":" + key.fileSize() + ":";
        cache.keySet().removeIf(k -> k.startsWith(prefix));
    }

    @Override
    public void clear() {
        cache.clear();
    }

    private record CacheEntry<T>(T value) {}
}
