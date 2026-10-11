package com.ravi.jfrmcp.jfr;

import com.ravi.jfrmcp.model.PerformanceFinding;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Extension point for future JMC Core / rule-based analyzers.
 */
public interface JfrAdvancedAnalyzer {

    record AdvancedAnalysisResult(
        String analyzerName,
        List<PerformanceFinding> findings,
        List<String> recommendations
    ) {}

    Optional<AdvancedAnalysisResult> analyze(Path recording);
}
