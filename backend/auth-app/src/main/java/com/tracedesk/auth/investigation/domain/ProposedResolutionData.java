package com.tracedesk.auth.investigation.domain;

/** Pure validation rules for a QA-visible proposed resolution. */
public record ProposedResolutionData(String rootCause, String changeSummary, String verificationSteps, String fixReference) {
    private static final int TEXT_CODE_POINTS = 4_000;
    private static final int TEXT_BYTES = 16_000;

    public static boolean valid(ProposedResolutionData data) {
        return data != null
                && InvestigationText.valid(data.rootCause, TEXT_CODE_POINTS, TEXT_BYTES, true)
                && InvestigationText.valid(data.changeSummary, TEXT_CODE_POINTS, TEXT_BYTES, true)
                && InvestigationText.valid(data.verificationSteps, TEXT_CODE_POINTS, TEXT_BYTES, true)
                && InvestigationText.valid(data.fixReference, 200, 800, false);
    }
}
