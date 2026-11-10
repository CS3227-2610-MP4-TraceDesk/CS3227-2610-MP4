package com.tracedesk.auth.investigation.ai;

import java.util.List;
import java.util.Set;

/** Experiment signature only. Invalid output uses ReportService.Failure (502 AI_INVALID_OUTPUT). */
public final class InvestigationAiOutput {
    private InvestigationAiOutput() { throw new UnsupportedOperationException("not implemented"); }
    public record Hypothesis(String statement, String rationale, List<String> sources) { }
    public record Result(List<Hypothesis> hypotheses, List<String> nextSteps) { }
    public static Result parse(String completion, Set<String> sentLabels) {
        throw new UnsupportedOperationException("not implemented");
    }
}
