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

class InvestigationAiC09HiddenTest {
    @Test void c09_instructionBoundAndPromptVersion() {
        var context=new Fixture().title("Synthetic title");
        assertFalse(context.systemInstruction().isBlank()); assertTrue(context.systemInstruction().getBytes(StandardCharsets.UTF_8).length<=1500);
        assertEquals("investigation-suggestion-v2",context.promptVersion()); assertEquals(3,context.sourceVersion());
    }
}
