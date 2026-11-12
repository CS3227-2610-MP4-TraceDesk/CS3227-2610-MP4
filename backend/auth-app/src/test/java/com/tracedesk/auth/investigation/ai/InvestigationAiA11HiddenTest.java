package com.tracedesk.auth.investigation.ai;

import com.tracedesk.ai.gateway.*;
import com.tracedesk.auth.ai.persistence.JdbcAiLedger;
import com.tracedesk.auth.investigation.application.InvestigationRepository.Note;
import com.tracedesk.auth.investigation.persistence.InvestigationStore;
import com.tracedesk.auth.reports.application.ReportRepository;
import com.tracedesk.auth.reports.application.ReportService;
import com.tracedesk.auth.reports.domain.ReportData;
import com.tracedesk.testing.IsolatedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import static com.tracedesk.ai.gateway.GatewayPorts.*;
import static com.tracedesk.ai.gateway.GatewayTypes.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.tracedesk.auth.investigation.ai.InvestigationAiAccessTest.*;

class InvestigationAiA11HiddenTest {
    @Test void a11_roleErrorWinsWhenDisabled() throws Exception {
        try(var f=new Fixture()) { f.policy=Policy.disabled(); f.live.set(f.reporter); f.denied("FORBIDDEN",403,f::suggest); f.noAdmission(); }
    }
}
