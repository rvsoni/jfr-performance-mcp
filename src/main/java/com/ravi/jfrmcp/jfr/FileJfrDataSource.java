package com.ravi.jfrmcp.jfr;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * File-backed implementation of JfrDataSource for offline JFR file analysis.
 */
public class FileJfrDataSource implements JfrDataSource {

    private final Path filePath;

    public FileJfrDataSource(Path filePath) {
        this.filePath = Objects.requireNonNull(filePath, "filePath cannot be null").toAbsolutePath().normalize();
    }

    @Override
    public String getIdentifier() {
        return filePath.getFileName().toString();
    }

    @Override
    public Optional<Path> getFilePath() {
        return Optional.of(filePath);
    }

    @Override
    public boolean isLive() {
        return false;
    }

    @Override
    public void close() throws IOException {
        // No persistent handle held at data source level
    }
}
