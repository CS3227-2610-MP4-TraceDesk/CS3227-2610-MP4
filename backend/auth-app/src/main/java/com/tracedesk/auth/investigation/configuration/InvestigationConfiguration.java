package com.tracedesk.auth.investigation.configuration;

import com.tracedesk.auth.investigation.application.InvestigationRepository;
import com.tracedesk.auth.investigation.application.InvestigationService;
import com.tracedesk.auth.reports.domain.ReportCursorSigner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;

@Configuration
public class InvestigationConfiguration {
    @Bean InvestigationService investigationService(InvestigationRepository repository,
                                                      ReportCursorSigner cursors, Clock clock) {
        return new InvestigationService(repository, cursors, clock);
    }
}
