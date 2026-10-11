package com.ravi.jfrmcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Collections;
import java.util.List;

/**
 * Metadata about the JVM, runtime environment, and host operating system.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JvmInfo(
    String version,
    String vendor,
    String runtime,
    String vmName,
    String vmVersion,
    List<String> jvmArguments,
    String os,
    Integer cpuCount,
    Long totalMemoryBytes,
    Long initialHeapBytes,
    Long maxHeapBytes
) {
    public JvmInfo {
        jvmArguments = jvmArguments != null ? List.copyOf(jvmArguments) : Collections.emptyList();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String version;
        private String vendor;
        private String runtime;
        private String vmName;
        private String vmVersion;
        private List<String> jvmArguments = Collections.emptyList();
        private String os;
        private Integer cpuCount;
        private Long totalMemoryBytes;
        private Long initialHeapBytes;
        private Long maxHeapBytes;

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder vendor(String vendor) {
            this.vendor = vendor;
            return this;
        }

        public Builder runtime(String runtime) {
            this.runtime = runtime;
            return this;
        }

        public Builder vmName(String vmName) {
            this.vmName = vmName;
            return this;
        }

        public Builder vmVersion(String vmVersion) {
            this.vmVersion = vmVersion;
            return this;
        }

        public Builder jvmArguments(List<String> jvmArguments) {
            this.jvmArguments = jvmArguments;
            return this;
        }

        public Builder os(String os) {
            this.os = os;
            return this;
        }

        public Builder cpuCount(Integer cpuCount) {
            this.cpuCount = cpuCount;
            return this;
        }

        public Builder totalMemoryBytes(Long totalMemoryBytes) {
            this.totalMemoryBytes = totalMemoryBytes;
            return this;
        }

        public Builder initialHeapBytes(Long initialHeapBytes) {
            this.initialHeapBytes = initialHeapBytes;
            return this;
        }

        public Builder maxHeapBytes(Long maxHeapBytes) {
            this.maxHeapBytes = maxHeapBytes;
            return this;
        }

        public JvmInfo build() {
            return new JvmInfo(version, vendor, runtime, vmName, vmVersion, jvmArguments, os, cpuCount, totalMemoryBytes, initialHeapBytes, maxHeapBytes);
        }
    }
}
