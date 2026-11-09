package com.tracedesk.auth.investigation.application;

import com.tracedesk.auth.investigation.domain.InvestigationNoteData;
import com.tracedesk.auth.investigation.domain.ProposedResolutionData;
import com.tracedesk.auth.reports.application.ReportRepository;
import com.tracedesk.auth.reports.application.ReportService;
import com.tracedesk.auth.reports.domain.ReportCursorSigner;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

public final class InvestigationService {
    public record NotesResult(java.util.List<InvestigationRepository.Note> items, String nextCursor) { }
    public record ResolutionsResult(java.util.List<InvestigationRepository.Resolution> items, String nextCursor) { }
    private final InvestigationRepository repository;
    private final ReportCursorSigner cursors;
    private final Clock clock;

    public InvestigationService(InvestigationRepository repository, ReportCursorSigner cursors, Clock clock) {
        this.repository = repository; this.cursors = cursors; this.clock = clock;
    }

    public ReportRepository.Membership developerContext(UUID actor, UUID project) {
        return requireDeveloper(actor, project);
    }

    public ReportRepository.Report start(UUID actor, UUID project, UUID report, int expectedVersion, UUID requestId) {
        requireDeveloper(actor, project);
        return repository.start(actor, project, report, expectedVersion, requestId, clock.instant());
    }

    public NotesResult notes(UUID actor, UUID project, UUID report, Integer requestedLimit, String cursor) {
        requireDeveloper(actor, project);
        if (requestedLimit != null && (requestedLimit < 1 || requestedLimit > 100)) throw failure("INVALID_REQUEST", 400);
        int limit = requestedLimit == null ? 25 : requestedLimit;
        Instant after = null; UUID afterId = null; String filter = report.toString();
        if (cursor != null) {
            var decoded = cursors.verify(cursor, actor, project, "NOTE_LIST", filter, clock.instant());
            if (decoded == null) throw failure("INVALID_CURSOR", 400);
            after = decoded.updatedAt(); afterId = decoded.lastId();
        }
        var page = repository.notes(actor, project, report, limit, after, afterId);
        String next = null;
        if (page.hasMore() && !page.items().isEmpty()) {
            var last = page.items().get(page.items().size() - 1);
            next = cursors.issue(new ReportCursorSigner.Cursor(actor, project, "NOTE_LIST", filter,
                    last.createdAt(), last.id(), clock.millis()));
        }
        return new NotesResult(page.items(), next);
    }

    public InvestigationRepository.Note createNote(UUID actor, UUID project, UUID report, UUID key,
                                                     InvestigationNoteData data, UUID requestId) {
        requireDeveloper(actor, project);
        return repository.createNote(actor, project, report, key, data, requestId, clock.instant());
    }

    public InvestigationRepository.Note updateNote(UUID actor, UUID project, UUID report, UUID note,
                                                     int expectedVersion, InvestigationNoteData data, UUID requestId) {
        requireDeveloper(actor, project);
        return repository.updateNote(actor, project, report, note, expectedVersion, data, requestId, clock.instant());
    }

    public void deleteNote(UUID actor, UUID project, UUID report, UUID note, int expectedVersion, UUID requestId) {
        requireDeveloper(actor, project);
        repository.deleteNote(actor, project, report, note, expectedVersion, requestId, clock.instant());
    }

    public InvestigationRepository.Proposal propose(UUID actor, UUID project, UUID report, ProposedResolutionData data,
                                                    int expectedVersion, UUID requestId) {
        requireDeveloper(actor, project);
        return repository.propose(actor, project, report, data, expectedVersion, requestId, clock.instant());
    }

    public ResolutionsResult resolutions(UUID actor, UUID project, UUID report, Integer requestedLimit, String cursor) {
        requireStaff(actor, project);
        if (requestedLimit != null && (requestedLimit < 1 || requestedLimit > 100)) throw failure("INVALID_REQUEST", 400);
        int limit = requestedLimit == null ? 25 : requestedLimit;
        UUID afterId = null; String filter = report.toString();
        if (cursor != null) {
            var decoded = cursors.verify(cursor, actor, project, "RESOLUTION_LIST", filter, clock.instant());
            if (decoded == null) throw failure("INVALID_CURSOR", 400);
            afterId = decoded.lastId();
        }
        var page = repository.resolutions(actor, project, report, limit, afterId);
        String next = null;
        if (page.hasMore() && !page.items().isEmpty()) {
            var last = page.items().get(page.items().size() - 1);
            next = cursors.issue(new ReportCursorSigner.Cursor(actor, project, "RESOLUTION_LIST", filter,
                    last.createdAt(), last.id(), clock.millis()));
        }
        return new ResolutionsResult(page.items(), next);
    }

    private ReportRepository.Membership requireDeveloper(UUID actor, UUID project) {
        var membership = requireActive(actor, project);
        if (!"DEVELOPER".equals(membership.role())) throw failure("FORBIDDEN", 403);
        return membership;
    }
    private void requireStaff(UUID actor, UUID project) {
        var membership = requireActive(actor, project);
        if (!"DEVELOPER".equals(membership.role()) && !"QA_REVIEWER".equals(membership.role())) throw failure("FORBIDDEN", 403);
    }
    private ReportRepository.Membership requireActive(UUID actor, UUID project) {
        return repository.membership(actor, project)
                .filter(m -> m.userEnabled() && m.projectEnabled() && m.active())
                .orElseThrow(() -> failure("NOT_FOUND", 404));
    }
    private static ReportService.Failure failure(String code, int status) { return new ReportService.Failure(code, status); }
}
