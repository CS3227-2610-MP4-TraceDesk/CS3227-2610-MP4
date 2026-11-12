package com.tracedesk.auth.reports.domain;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ReportActivityCursorTest {
    @Test void bindsIdentityReportAndFifteenMinuteLifetimeWithoutNarrowingBigint() {
        var signer=new ReportActivityCursor(); UUID actor=UUID.randomUUID(),project=UUID.randomUUID(),report=UUID.randomUUID();
        Instant now=Instant.parse("2026-11-12T00:00:00.123456Z"); long largeId=9_223_372_036_854_775_000L;
        String token=signer.issue(new ReportActivityCursor.Position(actor,project,report,now,largeId,now.toEpochMilli()));
        assertEquals(largeId,signer.verify(token,actor,project,report,now).eventId());
        assertNull(signer.verify(token,UUID.randomUUID(),project,report,now));
        assertNull(signer.verify(token,actor,UUID.randomUUID(),report,now));
        assertNull(signer.verify(token,actor,project,UUID.randomUUID(),now));
        assertNotNull(signer.verify(token,actor,project,report,now.plusMillis(899_999)));
        assertNull(signer.verify(token,actor,project,report,now.plusMillis(900_000)));
        int separator=token.indexOf('.');
        String tampered=token.substring(0,separator+1)+(token.charAt(separator+1)=='A'?'B':'A')+token.substring(separator+2);
        assertNull(signer.verify(tampered,actor,project,report,now));
    }
}
