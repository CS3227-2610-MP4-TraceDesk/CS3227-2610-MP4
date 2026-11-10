package com.tracedesk.auth.investigation.ai;

import com.tracedesk.auth.reports.application.ReportService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.tracedesk.auth.investigation.ai.InvestigationAiOutputTest.*;

class InvestigationAiV07HiddenTest {
    @Test void v07_duplicateKeyRejected() { rejected("{\"hypotheses\":[],\"hypotheses\":[],\"nextSteps\":[]}"); }
}
