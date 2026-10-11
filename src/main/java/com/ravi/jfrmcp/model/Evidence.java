package com.ravi.jfrmcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Structured, factual evidence extracted from JFR events.
 * Represents measured facts without subjective interpretation.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Evidence(
    String metric,
    Object value,
    String unit,
    String source,
    Instant startTime,
    Instant endTime,
    Double confidence,
    String description
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String metric;
        private Object value;
        private String unit;
        private String source;
        private Instant startTime;
        private Instant endTime;
        private Double confidence;
        private String description;

        public Builder metric(String metric) {
            this.metric = metric;
            return this;
        }

        public Builder value(Object value) {
            this.value = value;
            return this;
        }

        public Builder unit(String unit) {
            this.unit = unit;
            return this;
        }

        public Builder source(String source) {
            this.source = source;
            return this;
        }

        public Builder startTime(Instant startTime) {
            this.startTime = startTime;
            return this;
        }

        public Builder endTime(Instant endTime) {
            this.endTime = endTime;
            return this;
        }

        public Builder confidence(Double confidence) {
            this.confidence = confidence;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Evidence build() {
            return new Evidence(metric, value, unit, source, startTime, endTime, confidence, description);
        }
    }
}
