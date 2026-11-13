package com.tracedesk.auth.reports.application;

import com.tracedesk.auth.reports.domain.ReportData;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReportRepository {
    record Membership(UUID projectId, String projectName, boolean userEnabled, boolean projectEnabled,
                      boolean active, String role) { }
    record Report(UUID id, UUID projectId, UUID reporterUserId, ReportData fields, String status,
                  UUID assignedDeveloperId, Instant createdAt, Instant updatedAt, int version) { }
    record Summary(UUID id, UUID projectId, UUID reporterUserId, String title, String status,
                   UUID assignedDeveloperId, Instant createdAt, Instant updatedAt, int version) { }
    record Page(List<Summary> items, boolean hasMore) { }
    record Assignment(UUID assignedDeveloperId, int version, Instant updatedAt) { }
    record AssignmentCandidate(UUID id, String login) { }
    record AssignmentCandidates(UUID assignedDeveloperId, String assignedDeveloperLogin,
                                boolean assignmentEligible, int version,
                                List<AssignmentCandidate> items, boolean hasMore) { }
    record Draft(UUID id, UUID projectId, UUID ownerId, ReportData fields, Instant createdAt,
                 Instant updatedAt, Instant expiresAt, int version, UUID submittedReportId) { }
    record DraftSummary(UUID id, UUID projectId, String title, Instant createdAt, Instant updatedAt,
                        Instant expiresAt, int version, UUID submittedReportId) { }
    record DraftPage(List<DraftSummary> items, boolean hasMore) { }
    record Activity(String id, String eventCode, Instant occurredAt, String priorStatus, String newStatus,
                    Integer priorVersion, Integer newVersion) { }
    record ActivityPage(List<Activity> items, boolean hasMore) { }
    Optional<Membership> membership(UUID actor, UUID project);
    Report create(UUID actor, UUID project, UUID idempotencyKey, ReportData data, UUID requestId, Instant now);
    Page list(UUID actor, UUID project, String status, Boolean assignedToMe, int limit, Instant beforeUpdatedAt, UUID beforeId);
    Optional<Report> detail(UUID actor, UUID project, UUID report);
    AssignmentCandidates assignmentCandidates(UUID actor, UUID project, UUID report, UUID after, int limit);
    Assignment changeAssignment(UUID actor, UUID project, UUID report, UUID developer, String reason,
                                int expectedVersion, UUID requestId, Instant now);
    Assignment claim(UUID actor, UUID project, UUID report, int expectedVersion, UUID requestId, Instant now);
    Assignment release(UUID actor, UUID project, UUID report, String reason, int expectedVersion,
                       UUID requestId, Instant now);
    Draft createDraft(UUID actor, UUID project, UUID key, ReportData fields, UUID requestId, Instant now);
    DraftPage listDrafts(UUID actor, UUID project, int limit, Instant beforeUpdatedAt, UUID beforeId, Instant now);
    Optional<Draft> draft(UUID actor, UUID project, UUID draft, Instant now);
    Draft updateDraft(UUID actor, UUID project, UUID draft, int expectedVersion, ReportData fields, UUID requestId, Instant now);
    void deleteDraft(UUID actor, UUID project, UUID draft, int expectedVersion, UUID requestId, Instant now);
    Report updateReport(UUID actor, UUID project, UUID report, int expectedVersion, ReportData fields, UUID requestId, Instant now);
    Report submitDraft(UUID actor, UUID project, UUID draft, int expectedVersion, UUID key, UUID requestId, Instant now);
    ActivityPage activity(UUID actor, UUID project, UUID report, int limit, Instant afterOccurredAt, Long afterId);
}
