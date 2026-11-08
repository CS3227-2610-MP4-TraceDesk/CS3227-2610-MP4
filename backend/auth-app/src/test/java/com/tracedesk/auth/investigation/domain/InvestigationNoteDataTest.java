package com.tracedesk.auth.investigation.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InvestigationNoteDataTest {
    @Test void enforcesUnicodeBlankAndControlRules() {
        assertTrue(InvestigationNoteData.valid(new InvestigationNoteData("🙂".repeat(8_000))));
        assertFalse(InvestigationNoteData.valid(new InvestigationNoteData("🙂".repeat(8_001))));
        assertFalse(InvestigationNoteData.valid(new InvestigationNoteData("é".repeat(8_001))));
        assertFalse(InvestigationNoteData.valid(new InvestigationNoteData(" \t\n\r\u00a0")));
        assertFalse(InvestigationNoteData.valid(new InvestigationNoteData("note\u0000")));
        assertFalse(InvestigationNoteData.valid(new InvestigationNoteData("note\u0085")));
        assertFalse(InvestigationNoteData.valid(new InvestigationNoteData("note\ud800")));
        assertTrue(InvestigationNoteData.valid(new InvestigationNoteData("line one\nline two\t🙂")));
    }

    @Test void digestIsBodyOnlyAndSensitiveToSupplementaryCharacters() {
        assertArrayEquals(new InvestigationNoteData("same").sha256(), new InvestigationNoteData("same").sha256());
        assertFalse(java.security.MessageDigest.isEqual(
                new InvestigationNoteData("😀").sha256(), new InvestigationNoteData("😁").sha256()));
    }
}
