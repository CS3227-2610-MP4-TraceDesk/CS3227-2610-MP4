package com.tracedesk.auth.investigation.ai;

import com.tracedesk.auth.reports.application.ReportService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.tracedesk.auth.investigation.ai.InvestigationAiOutputTest.*;

class InvestigationAiV17HiddenTest {
    @Test void v17_unpairedSurrogatesRejected() {
        for (String escape : List.of("\\ud800","\\udc00")) assertAll(
            () -> rejected("{\"hypotheses\":[{\"statement\":\""+escape+"\",\"rationale\":\"r\",\"sources\":[\"title\"]}],\"nextSteps\":[]}"),
            () -> rejected("{\"hypotheses\":[{\"statement\":\"s\",\"rationale\":\""+escape+"\",\"sources\":[\"title\"]}],\"nextSteps\":[]}"),
            () -> rejected("{\"hypotheses\":[],\"nextSteps\":[\""+escape+"\"]}"));
    }
}
