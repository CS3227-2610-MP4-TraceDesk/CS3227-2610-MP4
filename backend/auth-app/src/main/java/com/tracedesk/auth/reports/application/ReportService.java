package com.tracedesk.auth.reports.application;

import com.tracedesk.auth.reports.domain.ReportAccessPolicy;
import com.tracedesk.auth.reports.domain.ReportCursorSigner;
import com.tracedesk.auth.reports.domain.ReportData;
import com.tracedesk.auth.reports.domain.ReportActivityCursor;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public final class ReportService {
    public static final class Failure extends RuntimeException {
        private final String code; private final int status; private final Integer currentVersion;
        public Failure(String code, int status) { this(code,status,null); }
        public Failure(String code, int status, Integer currentVersion) { super(code); this.code=code; this.status=status; this.currentVersion=currentVersion; }
        public String code() { return code; } public int status() { return status; } public Integer currentVersion() { return currentVersion; }
    }
    public record ListResult(java.util.List<ReportRepository.Summary> items, String nextCursor) { }
    public record AssignmentPage(ReportRepository.AssignmentCandidates candidates, String nextCursor) { }
    private static final int ASSIGNMENT_PAGE_SIZE = 25;
    private static final String ASSIGNMENT_CURSOR = "QA_ASSIGNMENT";
    private final ReportRepository repository; private final ReportCursorSigner cursors; private final ReportActivityCursor activityCursors; private final Clock clock;
    public ReportService(ReportRepository repository, ReportCursorSigner cursors, Clock clock) { this(repository,cursors,new ReportActivityCursor(),clock); }
    public ReportService(ReportRepository repository, ReportCursorSigner cursors, ReportActivityCursor activityCursors, Clock clock) { this.repository=repository; this.cursors=cursors; this.activityCursors=activityCursors; this.clock=clock; }

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
    public AssignmentPage assignmentCandidates(UUID actor, UUID project, UUID report, String cursor) {
        if (!"QA_REVIEWER".equals(requireMembership(actor, project).role())) throw new Failure("FORBIDDEN", 403);
        UUID after = null;
        if (cursor != null) {
            var decoded = cursors.verify(cursor, actor, project, ASSIGNMENT_CURSOR, report.toString(), clock.instant());
            if (decoded == null) throw new Failure("INVALID_CURSOR", 400);
            after = decoded.lastId();
        }
        var page = repository.assignmentCandidates(actor, project, report, after, ASSIGNMENT_PAGE_SIZE);
        String next = page.hasMore() ? cursors.issue(new ReportCursorSigner.Cursor(
                actor, project, ASSIGNMENT_CURSOR, report.toString(), Instant.EPOCH,
                page.items().getLast(), clock.millis())) : null;
        return new AssignmentPage(page, next);
    }

    public ReportRepository.Assignment changeAssignment(UUID actor, UUID project, UUID report, UUID developer,
                                                         String reason, int version, UUID requestId) {
        return repository.changeAssignment(actor, project, report, developer, reason, version, requestId, clock.instant());
    }
    public record ActivityResult(java.util.List<ReportRepository.Activity> items, String nextCursor) { }
    public ActivityResult activity(UUID actor, UUID project, UUID report, Integer requestedLimit, String cursor) {
        var membership = requireMembership(actor, project);
        if (!"REPORTER".equals(membership.role())) throw new Failure("FORBIDDEN",403);
        if (requestedLimit != null && (requestedLimit < 1 || requestedLimit > 100)) throw new Failure("INVALID_REQUEST",400);
        int limit=requestedLimit==null?25:requestedLimit;
        Instant after=null; Long afterId=null;
        if(cursor!=null) {
            var decoded=activityCursors.verify(cursor,actor,project,report,clock.instant());
            if(decoded==null) throw new Failure("INVALID_CURSOR",400);
            after=decoded.occurredAt(); afterId=decoded.eventId();
        }
        var page=repository.activity(actor,project,report,limit,after,afterId);
        String next=null;
        if(page.hasMore()&&!page.items().isEmpty()) {
            var last=page.items().get(page.items().size()-1);
            next=activityCursors.issue(new ReportActivityCursor.Position(actor,project,report,last.occurredAt(),Long.parseLong(last.id()),clock.millis()));
        }
        return new ActivityResult(page.items(),next);
    }
    public ReportRepository.Membership reporterContext(UUID actor, UUID project) {
        var membership = requireMembership(actor, project);
        if (!ReportAccessPolicy.mayCreate(facts(membership, null))) throw new Failure("FORBIDDEN", 403);
        return membership;
    }
    public ReportRepository.Draft createDraft(UUID actor, UUID project, UUID key, ReportData fields, UUID requestId) {
        var m = requireMembership(actor, project);
        if (!ReportAccessPolicy.mayCreate(facts(m, null))) throw new Failure("FORBIDDEN",403);
        if (ReportData.validateDraft(fields) != null) throw new Failure("VALIDATION_ERROR",400);
        return repository.createDraft(actor, project, key, fields, requestId, clock.instant());
    }
    public ListResultDraft listDrafts(UUID actor, UUID project, Integer requestedLimit, String cursor) {
        var m = requireMembership(actor, project);
        if (!ReportAccessPolicy.mayCreate(facts(m, null))) throw new Failure("FORBIDDEN",403);
        if (requestedLimit != null && (requestedLimit < 1 || requestedLimit > 100)) throw new Failure("INVALID_REQUEST",400);
        int limit = requestedLimit == null ? 25 : requestedLimit;
        Instant before = null; UUID beforeId = null;
        if (cursor != null) {
            var decoded = cursors.verify(cursor, actor, project, "DRAFT_LIST", "-", clock.instant());
            if (decoded == null) throw new Failure("INVALID_CURSOR",400);
            before = decoded.updatedAt(); beforeId = decoded.lastId();
        }
        var page = repository.listDrafts(actor, project, limit, before, beforeId, clock.instant());
        String next = null;
        if (page.hasMore() && !page.items().isEmpty()) {
            var last = page.items().get(page.items().size()-1);
            next = cursors.issue(new ReportCursorSigner.Cursor(actor, project, "DRAFT_LIST", "-", last.updatedAt(), last.id(), clock.millis()));
        }
        return new ListResultDraft(page.items(), next);
    }
    public record ListResultDraft(java.util.List<ReportRepository.DraftSummary> items, String nextCursor) { }
    public ReportRepository.Draft draft(UUID actor, UUID project, UUID draft) {
        var m = requireMembership(actor, project);
        if (!ReportAccessPolicy.mayCreate(facts(m, null))) throw new Failure("FORBIDDEN",403);
        return repository.draft(actor, project, draft, clock.instant()).orElseThrow(() -> new Failure("NOT_FOUND",404));
    }
    public ReportRepository.Draft updateDraft(UUID actor, UUID project, UUID draft, int version, ReportData fields, UUID requestId) {
        var m = requireMembership(actor, project);
        if (!ReportAccessPolicy.mayCreate(facts(m, null))) throw new Failure("FORBIDDEN",403);
        return repository.updateDraft(actor, project, draft, version, fields, requestId, clock.instant());
    }
    public void deleteDraft(UUID actor, UUID project, UUID draft, int version, UUID requestId) {
        var m = requireMembership(actor, project);
        if (!ReportAccessPolicy.mayCreate(facts(m, null))) throw new Failure("FORBIDDEN",403);
        repository.deleteDraft(actor, project, draft, version, requestId, clock.instant());
    }
    public ReportRepository.Report updateReport(UUID actor, UUID project, UUID report, int version, ReportData fields, UUID requestId) {
        var m = requireMembership(actor, project);
        if (!ReportAccessPolicy.mayCreate(facts(m, null))) throw new Failure("FORBIDDEN",403);
        return repository.updateReport(actor, project, report, version, fields, requestId, clock.instant());
    }
    public ReportRepository.Report submitDraft(UUID actor, UUID project, UUID draft, int version, UUID key, UUID requestId) {
        var m = requireMembership(actor, project);
        if (!ReportAccessPolicy.mayCreate(facts(m, null))) throw new Failure("FORBIDDEN",403);
        return repository.submitDraft(actor, project, draft, version, key, requestId, clock.instant());
    }
    private ReportRepository.Membership requireMembership(UUID actor, UUID project) {
        return repository.membership(actor, project).filter(m -> m.userEnabled() && m.projectEnabled() && m.active())
                .orElseThrow(() -> new Failure("NOT_FOUND", 404));
    }
    private static ReportAccessPolicy.Facts facts(ReportRepository.Membership m, UUID owner) {
        return new ReportAccessPolicy.Facts(m.userEnabled(), m.projectEnabled(), m.active(), m.role(), owner);
    }
}
