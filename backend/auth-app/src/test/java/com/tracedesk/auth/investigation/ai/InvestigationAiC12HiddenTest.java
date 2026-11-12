package com.tracedesk.auth.investigation.ai;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.tracedesk.auth.investigation.ai.InvestigationAiContextTest.*;

class InvestigationAiC12HiddenTest {
    @Test void c12_contextCeilingCountsUtf8BytesForTwoAndFourByteCharacters() {
        var f=new Fixture();
        int available=8000-units(f.description("x"));
        String multi="\u00e9".repeat(20)+"\ud83d\ude00".repeat(20);
        int multiBytes=multi.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        String exact="x"+multi+"a".repeat(available-multiBytes);
        var accepted=f.description(exact); assertEquals(8000,units(accepted));
        assertEquals(exact,JSON.readTree(accepted.dataJson()).get("report").get("description").asString());
        failure("AI_CONTEXT_TOO_LARGE",400,() -> f.description(exact+"a"));
    }
}
