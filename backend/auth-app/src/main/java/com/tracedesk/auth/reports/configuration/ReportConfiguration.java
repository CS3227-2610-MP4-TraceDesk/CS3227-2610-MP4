package com.tracedesk.auth.reports.configuration;

import com.tracedesk.auth.reports.application.ReportRepository;
import com.tracedesk.auth.reports.application.ReportService;
import com.tracedesk.auth.reports.domain.ReportCursorSigner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;

@Configuration
public class ReportConfiguration {
    @Bean ReportCursorSigner reportCursorSigner() { return new ReportCursorSigner(); }
    @Bean ReportService reportService(ReportRepository repository, ReportCursorSigner signer, Clock clock) {
        return new ReportService(repository, signer, clock);
    }
}
