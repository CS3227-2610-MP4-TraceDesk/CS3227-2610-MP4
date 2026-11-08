package com.tracedesk.auth.reports.ai;

import com.tracedesk.ai.gateway.GatewayTypes.Policy;
import com.tracedesk.ai.internal.soclaas.*;
import com.tracedesk.auth.ai.persistence.JdbcAiLedger;
import com.tracedesk.auth.reports.application.ReportRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.Clock;
import java.util.Map;

@Configuration
public class ReportAiConfiguration {
    /** Enablement requires a separately verified funding/deployment integration. No environment switch bypasses it. */
    @Bean(destroyMethod="close") @ConditionalOnMissingBean(ReportAiService.class)
    ReportAiService reporterAiService(ReportRepository repository,JdbcTemplate sql,Clock clock) {
        var ledger=new JdbcAiLedger(sql);var settings=SoclaasSettings.from(Map.of("AI_ENABLED","false"));
        return new ReportAiService(repository,clock,(actors,authorizer,contexts)->SoclaasGatewayFactory.create(
                settings,Policy.disabled(),null,actors,authorizer,contexts,ledger,clock));
    }
}
