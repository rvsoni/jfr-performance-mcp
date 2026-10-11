package com.ravi.jfrmcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Collections;
import java.util.List;

/**
 * An analytical finding representing a performance issue or condition detected in the recording.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PerformanceFinding(
    FindingCategory category,
    FindingSeverity severity,
    double confidence,
    String title,
    String summary,
    List<Evidence> evidence,
    String impact,
    List<String> recommendedInvestigation
) {
    public PerformanceFinding {
        evidence = evidence != null ? List.copyOf(evidence) : Collections.emptyList();
        recommendedInvestigation = recommendedInvestigation != null ? List.copyOf(recommendedInvestigation) : Collections.emptyList();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private FindingCategory category = FindingCategory.OTHER;
        private FindingSeverity severity = FindingSeverity.INFO;
        private double confidence = 1.0;
        private String title;
        private String summary;
        private List<Evidence> evidence = Collections.emptyList();
        private String impact;
        private List<String> recommendedInvestigation = Collections.emptyList();

        public Builder category(FindingCategory category) {
            this.category = category;
            return this;
        }

        public Builder severity(FindingSeverity severity) {
            this.severity = severity;
            return this;
        }

        public Builder confidence(double confidence) {
            this.confidence = confidence;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder summary(String summary) {
            this.summary = summary;
            return this;
        }

        public Builder evidence(List<Evidence> evidence) {
            this.evidence = evidence;
            return this;
        }

        public Builder impact(String impact) {
            this.impact = impact;
            return this;
        }

        public Builder recommendedInvestigation(List<String> recommendedInvestigation) {
            this.recommendedInvestigation = recommendedInvestigation;
            return this;
        }

        public PerformanceFinding build() {
            return new PerformanceFinding(category, severity, confidence, title, summary, evidence, impact, recommendedInvestigation);
        }
    }
}
