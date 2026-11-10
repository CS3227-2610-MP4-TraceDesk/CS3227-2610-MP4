package com.tracedesk.auth.investigation.ai;

import com.tracedesk.auth.investigation.application.InvestigationRepository.Note;
import com.tracedesk.auth.reports.application.ReportRepository;
import com.tracedesk.auth.reports.application.ReportService;
import com.tracedesk.auth.reports.domain.ReportData;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import static com.tracedesk.ai.gateway.GatewayTypes.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.tracedesk.auth.investigation.ai.InvestigationAiContextTest.*;

class InvestigationAiC02HiddenTest {
    @Test void c02_requestOrderDoesNotChangeSpecOrder() {
        var data = JSON.readTree(new Fixture().build(List.of("textLogs","title"),List.of()).dataJson());
        assertEquals(List.of("title","textLogs"),new ArrayList<>(data.get("report").propertyNames()));
        assertEquals("SAVED-LOG-CANARY",data.get("report").get("textLogs").asString());
    }
}
