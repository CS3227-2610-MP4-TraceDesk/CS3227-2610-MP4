package com.tracedesk.auth.reports.ai;

import com.tracedesk.ai.internal.soclaas.SoclaasSettings;
import com.tracedesk.auth.ai.configuration.SoclaasEnablementManager;
import com.tracedesk.auth.ai.persistence.JdbcAiLedger;
import com.tracedesk.auth.reports.application.ReportRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.Clock;
import java.util.Map;

@Configuration
public class ReportAiConfiguration {
    @Bean(destroyMethod="close") @ConditionalOnMissingBean
    @DependsOnDatabaseInitialization
    SoclaasEnablementManager soclaasEnablementManager(JdbcTemplate sql, Clock clock) {
        return new SoclaasEnablementManager(SoclaasSettings.from(System.getenv()),new JdbcAiLedger(sql),clock);
    }

    @Bean(destroyMethod="close") @ConditionalOnMissingBean(ReportAiService.class)
    ReportAiService reporterAiService(ReportRepository repository,Clock clock,SoclaasEnablementManager enablement) {
        return new ReportAiService(repository,clock,enablement::createGateway);
    }
}
