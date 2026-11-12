package com.tracedesk.auth.investigation.ai;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class InvestigationAiA05HiddenTest {
    @Test void a05_assignmentDenialPrecedesForeignNoteLookup() throws Exception {
        try(var f=new InvestigationAiAccessTest.Fixture()) {
            f.live.set(f.second);
            var selection=new InvestigationAiSelection(List.of("title"),List.of(new InvestigationAiSelection.SelectedNote(f.otherNote,1)));
            f.denied("FORBIDDEN",403,() -> f.suggest(f.report,1,selection));
            assertEquals(0,f.noteReads.get()); f.noAdmission();
        }
    }
}
