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

class InvestigationAiC13HiddenTest {
    @Test void c13_versionsCheckedOnValuesActuallyRead() {
        var f=new Fixture(); var note=f.note(10,NOW,"note",2);
        failure("STALE_VERSION",409,() -> f.build(List.of("title"),List.of(new InvestigationAiSelection.SelectedNote(note.id(),1))));
    }
}
