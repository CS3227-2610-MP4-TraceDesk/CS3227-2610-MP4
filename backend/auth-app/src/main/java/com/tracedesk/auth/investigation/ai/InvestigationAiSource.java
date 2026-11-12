package com.tracedesk.auth.investigation.ai;

import com.tracedesk.auth.reports.application.ReportRepository;
import com.tracedesk.auth.investigation.application.InvestigationRepository;
import java.util.Optional;
import java.util.UUID;

/** Read-only application port; implementations must read current saved values. */
public interface InvestigationAiSource {
    /** Account/project/membership state plus the fresh account role, never the legacy membership role. */
    Optional<ReportRepository.Membership> membership(UUID actor, UUID project);
    Optional<ReportRepository.Report> report(UUID project, UUID report);
    Optional<InvestigationRepository.Note> note(UUID project, UUID report, UUID note);
}
