package com.tracedesk.auth.reports.application;

import com.tracedesk.auth.reports.domain.ReportAccessPolicy;
import com.tracedesk.auth.reports.domain.ReportCursorSigner;
import com.tracedesk.auth.reports.domain.ReportData;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public final class ReportService {
    public static final class Failure extends RuntimeException {
        private final String code; private final int status;
        public Failure(String code, int status) { super(code); this.code=code; this.status=status; }
        public String code() { return code; } public int status() { return status; }
    }
    public record ListResult(java.util.List<ReportRepository.Summary> items, String nextCursor) { }
    private final ReportRepository repository; private final ReportCursorSigner cursors; private final Clock clock;
    public ReportService(ReportRepository repository, ReportCursorSigner cursors, Clock clock) { this.repository=repository; this.cursors=cursors; this.clock=clock; }

    public ReportRepository.Report create(UUID actor, UUID project, UUID key, ReportData fields, UUID requestId) {
        var membership = requireMembership(actor, project);
        if (!ReportAccessPolicy.mayCreate(facts(membership, null))) throw new Failure("FORBIDDEN", 403);
        if (ReportData.validate(fields) != null) throw new Failure("VALIDATION_ERROR", 400);
        return repository.create(actor, project, key, fields, requestId, clock.instant());
    }
    public ListResult list(UUID actor, UUID project, Integer requestedLimit, String cursor,
                           String status, Boolean assignedToMe) {
        var membership = requireMembership(actor, project);
        if (!ReportAccessPolicy.mayList(facts(membership, null))) throw new Failure("FORBIDDEN", 403);
        if (requestedLimit != null && (requestedLimit < 1 || requestedLimit > 100)) throw new Failure("INVALID_REQUEST", 400);
        if (status != null && !java.util.Set.of("OPEN", "IN_PROGRESS", "READY_FOR_QA", "CLOSED").contains(status)) throw new Failure("INVALID_REQUEST", 400);
        if (assignedToMe != null && "REPORTER".equals(membership.role())) throw new Failure("INVALID_REQUEST", 400);
        int limit = requestedLimit == null ? 25 : requestedLimit;
        String filter = (status == null ? "-" : status) + ":" + (assignedToMe == null ? "-" : assignedToMe.toString());
        Instant before = null; UUID beforeId = null;
        if (cursor != null) {
            var decoded = cursors.verify(cursor, actor, project, "REPORT_LIST", filter, clock.instant());
            if (decoded == null) throw new Failure("INVALID_CURSOR", 400);
            before = decoded.updatedAt(); beforeId = decoded.lastId();
        }
        var page = repository.list(actor, project, status, assignedToMe, limit, before, beforeId);
        String next = null;
        if (page.hasMore() && !page.items().isEmpty()) {
            var last = page.items().get(page.items().size()-1);
            next = cursors.issue(new ReportCursorSigner.Cursor(actor, project, "REPORT_LIST", filter, last.updatedAt(), last.id(), clock.millis()));
        }
        return new ListResult(page.items(), next);
    }
    public ReportRepository.Report detail(UUID actor, UUID project, UUID report) {
        var membership = requireMembership(actor, project);
        if (!ReportAccessPolicy.mayList(facts(membership, null))) throw new Failure("FORBIDDEN", 403);
        return repository.detail(actor, project, report).orElseThrow(() -> new Failure("NOT_FOUND", 404));
    }
    public ReportRepository.Membership reporterContext(UUID actor, UUID project) {
        var membership = requireMembership(actor, project);
        if (!ReportAccessPolicy.mayCreate(facts(membership, null))) throw new Failure("FORBIDDEN", 403);
        return membership;
    }
    private ReportRepository.Membership requireMembership(UUID actor, UUID project) {
        return repository.membership(actor, project).filter(m -> m.userEnabled() && m.projectEnabled() && m.active())
                .orElseThrow(() -> new Failure("NOT_FOUND", 404));
    }
    private static ReportAccessPolicy.Facts facts(ReportRepository.Membership m, UUID owner) {
        return new ReportAccessPolicy.Facts(m.userEnabled(), m.projectEnabled(), m.active(), m.role(), owner);
    }
}
