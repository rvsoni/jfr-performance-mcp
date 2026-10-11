package com.ravi.jfrmcp.model;

/**
 * Categorization of JVM performance findings.
 */
public enum FindingCategory {
    CPU,
    GC,
    ALLOCATION,
    THREAD,
    LOCK_CONTENTION,
    EXCEPTION,
    IO,
    LATENCY,
    JVM_CONFIGURATION,
    OTHER
}
