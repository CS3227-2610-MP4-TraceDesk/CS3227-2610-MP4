package com.tracedesk.auth.investigation.application;

import com.tracedesk.auth.investigation.domain.InvestigationNoteData;
import com.tracedesk.auth.investigation.domain.ProposedResolutionData;
import com.tracedesk.auth.reports.application.ReportRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvestigationRepository {
    record Note(UUID id, UUID reportId, UUID authorUserId, UUID lastEditedByUserId,
                String body, Instant createdAt, Instant updatedAt, int version) { }
    record NotePage(List<Note> items, boolean hasMore) { }
    record Rejection(String reason, UUID reviewerUserId, Instant createdAt) { }
    record Resolution(UUID id, UUID reportId, UUID authorUserId, int revision, String rootCause,
                      String changeSummary, String verificationSteps, String fixReference, Instant createdAt,
                      Rejection rejection) { }
    record ResolutionPage(List<Resolution> items, boolean hasMore) { }
    record Proposal(ReportRepository.Report report, Resolution resolution) { }
    Optional<ReportRepository.Membership> membership(UUID actor, UUID project);
    ReportRepository.Report start(UUID actor, UUID project, UUID report, int expectedVersion,
                                  UUID requestId, Instant now);
    NotePage notes(UUID actor, UUID project, UUID report, int limit, Instant afterCreatedAt, UUID afterId);
    Note createNote(UUID actor, UUID project, UUID report, UUID idempotencyKey,
                    InvestigationNoteData data, UUID requestId, Instant now);
    Note updateNote(UUID actor, UUID project, UUID report, UUID note, int expectedVersion,
                    InvestigationNoteData data, UUID requestId, Instant now);
    void deleteNote(UUID actor, UUID project, UUID report, UUID note, int expectedVersion,
                    UUID requestId, Instant now);
    Proposal propose(UUID actor, UUID project, UUID report, ProposedResolutionData data, int expectedVersion,
                     UUID requestId, Instant now);
    ResolutionPage resolutions(UUID actor, UUID project, UUID report, int limit, UUID afterId);
}
