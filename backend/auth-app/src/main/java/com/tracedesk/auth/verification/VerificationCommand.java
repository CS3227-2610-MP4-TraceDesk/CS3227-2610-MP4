package com.tracedesk.auth.verification;

import com.tracedesk.auth.reports.application.ReportService;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public record VerificationCommand(int expectedVersion, List<Check> checks, String notes, Decision decision, String reason) {
    public enum Decision { SAVE, CLOSE, SEND_BACK }
    public enum Result { Pending, Pass, Fail }
    public record Check(String title, Result result, String notes) { }

    public VerificationCommand {
        if (expectedVersion < 1 || checks == null || checks.size() > 30 || decision == null
                || !text(notes, 2000) || !text(reason, 2000)) throw invalid();
        Set<String> titles = new HashSet<>();
        for (Check check : checks) {
            if (check == null || !text(check.title(), 300) || blank(check.title()) || check.result() == null
                    || !text(check.notes(), 250) || !titles.add(check.title().strip().toLowerCase(Locale.ROOT))) throw invalid();
        }
        if (decision == Decision.SEND_BACK ? blank(reason) : !reason.isEmpty()) throw invalid();
        if (decision == Decision.CLOSE && (checks.isEmpty() || checks.stream().anyMatch(check -> check.result() != Result.Pass)))
            throw new ReportService.Failure("CHECKS_NOT_PASSED", 409);
        checks = List.copyOf(checks);
    }

    private static boolean blank(String value) {
        return value.codePoints().allMatch(cp -> Character.isWhitespace(cp) || Character.isSpaceChar(cp));
    }

    private static boolean text(String value, int limit) {
        return value != null && value.codePointCount(0, value.length()) <= limit && value.codePoints().noneMatch(
                cp -> (cp >= 0xd800 && cp <= 0xdfff) || (Character.isISOControl(cp) && cp != '\n' && cp != '\r' && cp != '\t'));
    }

    static ReportService.Failure invalid() { return new ReportService.Failure("INVALID_REQUEST", 400); }
}
