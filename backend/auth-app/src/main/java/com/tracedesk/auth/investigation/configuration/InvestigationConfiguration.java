package com.tracedesk.auth.investigation.configuration;

import com.tracedesk.auth.investigation.application.InvestigationRepository;
import com.tracedesk.auth.investigation.application.InvestigationService;
import com.tracedesk.auth.investigation.ai.InvestigationAiAccess;
import com.tracedesk.auth.investigation.ai.InvestigationAiSource;
import com.tracedesk.auth.ai.configuration.SoclaasEnablementManager;
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

    @Bean(destroyMethod="close") InvestigationAiAccess investigationAiAccess(
            InvestigationAiSource source, Clock clock, SoclaasEnablementManager enablement) {
        return new InvestigationAiAccess(source, clock, enablement::createGateway);
    }
}
