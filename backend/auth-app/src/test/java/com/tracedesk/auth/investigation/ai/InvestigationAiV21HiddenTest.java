package com.tracedesk.auth.investigation.ai;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class InvestigationAiV21HiddenTest {
    @Test void v21_spacesAndNfdAccentsPreservedByteForByte() {
        String text="  Cafe\u0301 synthetic  ";
        var result=InvestigationAiOutputTest.parse(InvestigationAiOutputTest.one(text,text,List.of(text)));
        for(String actual:List.of(result.hypotheses().getFirst().statement(),result.hypotheses().getFirst().rationale(),result.nextSteps().getFirst()))
            assertArrayEquals(text.getBytes(StandardCharsets.UTF_8),actual.getBytes(StandardCharsets.UTF_8));
    }
}
