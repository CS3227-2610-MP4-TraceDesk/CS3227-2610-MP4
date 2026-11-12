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

class InvestigationAiA14HiddenTest {
    @Test void a14_reassignmentAfterSelectionPreventsDispatch() throws Exception {
        try(var f=new Fixture()) {
            f.beforeDispatch=() -> f.sql.update("update td_reports set assigned_developer_id=? where id=?",f.second,f.report);
            f.denied("FORBIDDEN",403,f::suggest); assertEquals(1,f.contextReads.get()); assertEquals(0,f.calls.get());
        }
    }
}
