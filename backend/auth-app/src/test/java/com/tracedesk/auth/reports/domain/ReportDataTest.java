package com.tracedesk.auth.reports.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReportDataTest {
    private ReportData valid() { return new ReportData("title", "description", "environment", "steps", "expected", "actual", ""); }

    @Test void acceptsContractWhitespaceAndSupplementaryCharactersAtBounds() {
        ReportData withLines = new ReportData("🙂".repeat(160), "d\t\n\r", "env", "steps", "expected", "actual", "log\n");
        assertNull(ReportData.validate(withLines));
        assertEquals(640, withLines.title().getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
    }

    @Test void rejectsCodePointByteBlankControlAndUnpairedSurrogateViolations() {
        assertNotNull(ReportData.validate(new ReportData("🙂".repeat(161), "d", "e", "s", "e", "a", "")));
        assertNotNull(ReportData.validate(new ReportData("é".repeat(161), "d", "e", "s", "e", "a", "")));
        assertNotNull(ReportData.validate(new ReportData(" \t\n", "d", "e", "s", "e", "a", "")));
        assertNotNull(ReportData.validate(new ReportData("a\u000b", "d", "e", "s", "e", "a", "")));
        assertNotNull(ReportData.validate(new ReportData("a\u0085", "d", "e", "s", "e", "a", "")));
        assertNotNull(ReportData.validate(new ReportData("a\ud800", "d", "e", "s", "e", "a", "")));
        assertNotNull(ReportData.validate(new ReportData("a\udc00", "d", "e", "s", "e", "a", "")));
        assertNotNull(ReportData.validate(new ReportData("a\u0000", "d", "e", "s", "e", "a", "")));
    }

    @Test void validatesAssignmentReasonPresenceUnicodeLengthAndCharacters() {
        assertTrue(ReportData.validAssignmentReason(null, false));
        assertFalse(ReportData.validAssignmentReason(null, true));
        assertTrue(ReportData.validAssignmentReason("\u2003\u3000", false));
        assertFalse(ReportData.validAssignmentReason("\u2003\u3000", true));
        assertTrue(ReportData.validAssignmentReason("🙂".repeat(500), true));
        assertFalse(ReportData.validAssignmentReason("🙂".repeat(501), true));
        assertFalse(ReportData.validAssignmentReason("reason\u0000", true));
        assertFalse(ReportData.validAssignmentReason("reason\ud800", true));
        assertTrue(ReportData.validAssignmentReason("  reason \t\n", true));
    }

    @Test void enforcesTheExactCodePointAndUtf8LimitForEveryField() {
        int[] limits={160,10000,4000,8000,4000,4000,16000};
        for(int field=0;field<limits.length;field++) {
            String[] values={"t","d","e","s","e","a",""};
            values[field]="🙂".repeat(limits[field]);
            assertNull(ReportData.validate(data(values)),"exact limit must be accepted for field "+ReportData.FIELD_NAMES.get(field));
            values[field]+="🙂";
            assertNotNull(ReportData.validate(data(values)),"one supplementary code point over limit must fail for field "+ReportData.FIELD_NAMES.get(field));
        }
    }

    @Test void canonicalDigestPreservesSupplementaryCharactersAndContractFieldOrder() {
        ReportData first = new ReportData("😀", "d", "e", "s", "e", "a", "");
        ReportData second = new ReportData("😁", "d", "e", "s", "e", "a", "");
        assertFalse(java.security.MessageDigest.isEqual(first.sha256(), second.sha256()));
        assertEquals("{\"title\":\"x\",\"description\":\"d\",\"environment\":\"e\",\"reproductionSteps\":\"s\",\"expectedBehavior\":\"e\",\"actualBehavior\":\"a\",\"textLogs\":\"\"}",
                new String(new ReportData("x", "d", "e", "s", "e", "a", "").canonicalJson(), java.nio.charset.StandardCharsets.UTF_8));
    }
    private static ReportData data(String[] values) { return new ReportData(values[0],values[1],values[2],values[3],values[4],values[5],values[6]); }
}
