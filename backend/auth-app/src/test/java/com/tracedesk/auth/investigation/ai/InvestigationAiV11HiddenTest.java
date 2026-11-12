package com.tracedesk.auth.investigation.ai;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class InvestigationAiV11HiddenTest {
    @Test void v11_300EmojiAre300CodePointsAnd1200Bytes() {
        String text="\ud83d\ude00".repeat(300);
        assertEquals(300,text.codePointCount(0,text.length())); assertEquals(600,text.length());
        assertEquals(1200,text.getBytes(StandardCharsets.UTF_8).length);
        var result=InvestigationAiOutputTest.parse(InvestigationAiOutputTest.one(text,"r",List.of()));
        assertEquals(text,result.hypotheses().getFirst().statement());
    }
}
