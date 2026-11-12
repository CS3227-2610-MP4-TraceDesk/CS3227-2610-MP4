package com.tracedesk.auth.investigation.ai;

import com.tracedesk.auth.reports.application.ReportService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.tracedesk.auth.investigation.ai.InvestigationAiOutputTest.*;

class InvestigationAiV15HiddenTest {
    @Test void v15_forbiddenControlsInTextRejected() {
        for (String bad : List.of("x\u0000y","x\u0007y","x\u0085y")) assertAll(
            () -> rejected(one(bad,"r",List.of())), () -> rejected(one("s",bad,List.of())),
            () -> rejected(one("s","r",List.of(bad))));
    }
}
