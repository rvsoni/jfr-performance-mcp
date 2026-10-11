package com.ravi.jfrmcp.util;

import com.ravi.jfrmcp.exception.InvalidRecordingException;
import com.ravi.jfrmcp.exception.RecordingAccessException;
import com.ravi.jfrmcp.exception.RecordingNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PathValidatorTest {

    @TempDir
    Path tempDir;

    private Path allowedRecordingsDir;

    @BeforeEach
    void setUp() throws IOException {
        allowedRecordingsDir = tempDir.resolve("recordings");
        Files.createDirectories(allowedRecordingsDir);
    }

    @Test
    void shouldResolveValidJfrFile() throws IOException {
        Path jfrFile = allowedRecordingsDir.resolve("sample.jfr");
        Files.createFile(jfrFile);

        Path resolved = PathValidator.validateAndResolve(allowedRecordingsDir, "sample.jfr");
        assertThat(resolved).isEqualTo(jfrFile.toRealPath());
    }

    @Test
    void shouldRejectPathTraversal() {
        assertThatThrownBy(() -> PathValidator.validateAndResolve(allowedRecordingsDir, "../secret.jfr"))
            .isInstanceOf(RecordingAccessException.class)
            .hasMessageContaining("Path traversal detected");

        assertThatThrownBy(() -> PathValidator.validateAndResolve(allowedRecordingsDir, "../../etc/passwd"))
            .isInstanceOf(RecordingAccessException.class)
            .hasMessageContaining("Path traversal detected");
    }

    @Test
    void shouldRejectMissingFile() {
        assertThatThrownBy(() -> PathValidator.validateAndResolve(allowedRecordingsDir, "non-existent.jfr"))
            .isInstanceOf(RecordingNotFoundException.class)
            .hasMessageContaining("not found");
    }

    @Test
    void shouldRejectNonJfrFile() throws IOException {
        Path textFile = allowedRecordingsDir.resolve("notes.txt");
        Files.createFile(textFile);

        assertThatThrownBy(() -> PathValidator.validateAndResolve(allowedRecordingsDir, "notes.txt"))
            .isInstanceOf(InvalidRecordingException.class)
            .hasMessageContaining("Only '.jfr' files are supported");
    }

    @Test
    void shouldRejectDirectoryTarget() throws IOException {
        Path subDir = allowedRecordingsDir.resolve("nested.jfr");
        Files.createDirectories(subDir);

        assertThatThrownBy(() -> PathValidator.validateAndResolve(allowedRecordingsDir, "nested.jfr"))
            .isInstanceOf(InvalidRecordingException.class)
            .hasMessageContaining("is a directory");
    }

    @Test
    void shouldValidateDirectorySafely() throws IOException {
        Path sub = allowedRecordingsDir.resolve("prod");
        Files.createDirectories(sub);

        Path validated = PathValidator.validateDirectory(allowedRecordingsDir, "prod");
        assertThat(validated).isEqualTo(sub.toRealPath());

        // Default if null or blank
        assertThat(PathValidator.validateDirectory(allowedRecordingsDir, null))
            .isEqualTo(allowedRecordingsDir);

        // Path traversal rejection
        assertThatThrownBy(() -> PathValidator.validateDirectory(allowedRecordingsDir, "../sub"))
            .isInstanceOf(RecordingAccessException.class);
    }
}
