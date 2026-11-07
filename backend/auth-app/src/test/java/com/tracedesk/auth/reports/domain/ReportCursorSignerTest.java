package com.tracedesk.auth.reports.domain;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ReportCursorSignerTest {
    @Test void bindsActorProjectPurposeFilterAndFifteenMinuteLifetime() {
        var signer = new ReportCursorSigner(); UUID actor=UUID.randomUUID(), project=UUID.randomUUID(); Instant now=Instant.parse("2026-11-07T00:00:00Z");
        var cursor = new ReportCursorSigner.Cursor(actor,project,"REPORT_LIST","OPEN:-",now,UUID.randomUUID(),now.toEpochMilli());
        String token=signer.issue(cursor);
        assertNotNull(signer.verify(token,actor,project,"REPORT_LIST","OPEN:-",now));
        assertNull(signer.verify(token,UUID.randomUUID(),project,"REPORT_LIST","OPEN:-",now));
        assertNull(signer.verify(token,actor,project,"DETAIL","OPEN:-",now));
        assertNull(signer.verify(token,actor,project,"REPORT_LIST","CLOSED:-",now));
        assertNotNull(signer.verify(token,actor,project,"REPORT_LIST","OPEN:-",now.plusMillis(899_999)));
        assertNull(signer.verify(token,actor,project,"REPORT_LIST","OPEN:-",now.plusMillis(900_000)));
        int separator=token.indexOf('.'); char first=token.charAt(separator+1); String tampered=token.substring(0,separator+1)+(first=='A'?'B':'A')+token.substring(separator+2);
        assertNull(signer.verify(tampered,actor,project,"REPORT_LIST","OPEN:-",now));
        String[] parts=token.split("\\."); String alias=token.substring(0,separator+1)+nonCanonicalAlias(parts[1]);
        assertNull(signer.verify(alias,actor,project,"REPORT_LIST","OPEN:-",now),"noncanonical unused padding bits must be rejected");
        assertNull(signer.verify("x".repeat(4097),actor,project,"REPORT_LIST","OPEN:-",now));
    }
    private static String nonCanonicalAlias(String signature) {
        String alphabet="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
        int value=alphabet.indexOf(signature.charAt(signature.length()-1));
        return signature.substring(0,signature.length()-1)+alphabet.charAt(value | 1);
    }
}
