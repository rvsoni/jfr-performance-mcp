package com.ravi.jfrmcp.jfr;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Abstraction for JFR event data sources (e.g. offline recording files or future live event streams).
 */
public interface JfrDataSource extends Closeable {

    /**
     * Name or identifier of this data source.
     */
    String getIdentifier();

    /**
     * File path if this source is backed by a local file.
     */
    Optional<Path> getFilePath();

    /**
     * Whether this source is live/streaming or offline/static.
     */
    boolean isLive();

    @Override
    default void close() throws IOException {
    }
}
