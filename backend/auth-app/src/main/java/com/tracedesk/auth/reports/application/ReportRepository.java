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
    Optional<Membership> membership(UUID actor, UUID project);
    Report create(UUID actor, UUID project, UUID idempotencyKey, ReportData data, UUID requestId, Instant now);
    Page list(UUID actor, UUID project, String status, Boolean assignedToMe, int limit, Instant beforeUpdatedAt, UUID beforeId);
    Optional<Report> detail(UUID actor, UUID project, UUID report);
}
