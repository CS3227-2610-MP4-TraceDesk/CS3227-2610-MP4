package com.tracedesk.auth.investigation.ai;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static com.tracedesk.auth.investigation.ai.InvestigationAiContextTest.*;

class InvestigationAiC05HiddenTest {
    @Test void c05_equalCreationTimesUseAscendingUuidOrder() {
        var f=new Fixture();
        // Values straddle the signed-long boundary to catch Java UUID.compareTo ordering.
        var low=new com.tracedesk.auth.investigation.application.InvestigationRepository.Note(
            java.util.UUID.fromString("7fffffff-ffff-4fff-8000-000000000001"),REPORT,ACTOR,ACTOR,"lower",NOW,NOW,1);
        var high=new com.tracedesk.auth.investigation.application.InvestigationRepository.Note(
            java.util.UUID.fromString("80000000-0000-4000-8000-000000000001"),REPORT,ACTOR,ACTOR,"higher",NOW,NOW,1);
        f.notes.put(low.id(),low); f.notes.put(high.id(),high);
        var notes=JSON.readTree(f.build(List.of("title"),List.of(selected(high),selected(low))).dataJson()).get("notes");
        assertEquals(2,notes.size()); assertEquals("lower",notes.get(0).get("body").asString()); assertEquals("higher",notes.get(1).get("body").asString());
        assertEquals("note1",notes.get(0).get("ref").asString()); assertEquals("note2",notes.get(1).get("ref").asString());
    }
}
