package com.ravi.jfrmcp.util;

import com.ravi.jfrmcp.exception.InvalidRecordingException;
import com.ravi.jfrmcp.exception.RecordingAccessException;
import com.ravi.jfrmcp.exception.RecordingNotFoundException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Validates and resolves JFR recording file paths to enforce security boundaries.
 * Prevents directory traversal, symlink escapes, and unauthorized filesystem access.
 */
public final class PathValidator {

    private PathValidator() {
    }

    /**
     * Resolves and validates a recording path against the base directory.
     *
     * @param baseDir the configured allowed base directory
     * @param requestedPath the relative or absolute path requested by the client
     * @return the normalized, canonical Path
     * @throws RecordingAccessException if path traversal or security violation is detected
     * @throws RecordingNotFoundException if the file does not exist
     * @throws InvalidRecordingException if the file is not a .jfr file or is a directory
     */
    public static Path validateAndResolve(Path baseDir, String requestedPath) {
        if (requestedPath == null || requestedPath.isBlank()) {
            throw new RecordingAccessException("Recording path cannot be null or empty.");
        }

        // Reject explicit path traversal tokens
        if (requestedPath.contains("..")) {
            throw new RecordingAccessException("Path traversal detected in requested path: '" + requestedPath + "'");
        }

        Path normalizedBase;
        try {
            if (!Files.exists(baseDir)) {
                Files.createDirectories(baseDir);
            }
            normalizedBase = baseDir.toRealPath();
        } catch (IOException e) {
            normalizedBase = baseDir.toAbsolutePath().normalize();
        }

        Path target = normalizedBase.resolve(requestedPath).normalize();

        // Must start with base directory
        if (!target.startsWith(normalizedBase)) {
            throw new RecordingAccessException("Access denied: path '" + requestedPath + "' is outside allowed directory '" + normalizedBase + "'.");
        }

        if (!Files.exists(target)) {
            throw new RecordingNotFoundException("Recording '" + requestedPath + "' was not found in the configured recording directory.");
        }

        if (Files.isDirectory(target)) {
            throw new InvalidRecordingException("Target path '" + requestedPath + "' is a directory, not a JFR file.");
        }

        Path realTarget;
        try {
            realTarget = target.toRealPath();
        } catch (IOException e) {
            throw new RecordingAccessException("Unable to resolve real path for '" + requestedPath + "': " + e.getMessage(), e);
        }

        // Ensure real path (resolving symlinks) is still inside the allowed directory
        if (!realTarget.startsWith(normalizedBase)) {
            throw new RecordingAccessException("Symlink escape detected: '" + requestedPath + "' points outside allowed directory.");
        }

        String fileName = realTarget.getFileName().toString().toLowerCase();
        if (!fileName.endsWith(".jfr")) {
            throw new InvalidRecordingException("Invalid file extension for '" + requestedPath + "'. Only '.jfr' files are supported.");
        }

        return realTarget;
    }

    /**
     * Validates that an optional directory override is within or matches the allowed base directory.
     * Prevents clients from supplying arbitrary directories like /etc or /root.
     */
    public static Path validateDirectory(Path defaultBaseDir, String requestedDir) {
        if (requestedDir == null || requestedDir.isBlank()) {
            return defaultBaseDir;
        }

        if (requestedDir.contains("..")) {
            throw new RecordingAccessException("Path traversal detected in directory path: '" + requestedDir + "'");
        }

        Path normalizedBase = defaultBaseDir.toAbsolutePath().normalize();
        Path targetDir = normalizedBase.resolve(requestedDir).normalize();

        if (!targetDir.startsWith(normalizedBase)) {
            throw new RecordingAccessException("Access denied: directory '" + requestedDir + "' is outside allowed recording directory.");
        }

        if (!Files.exists(targetDir)) {
            throw new RecordingNotFoundException("Directory '" + requestedDir + "' does not exist.");
        }

        if (!Files.isDirectory(targetDir)) {
            throw new InvalidRecordingException("Path '" + requestedDir + "' is not a directory.");
        }

        try {
            Path realDir = targetDir.toRealPath();
            Path realBase = Files.exists(normalizedBase) ? normalizedBase.toRealPath() : normalizedBase;
            if (!realDir.startsWith(realBase)) {
                throw new RecordingAccessException("Symlink escape detected in directory '" + requestedDir + "'.");
            }
            return realDir;
        } catch (IOException e) {
            return targetDir;
        }
    }
}
