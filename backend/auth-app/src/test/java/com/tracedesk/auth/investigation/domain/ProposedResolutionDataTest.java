package com.tracedesk.auth.investigation.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProposedResolutionDataTest {
    private static ProposedResolutionData root(String value) { return new ProposedResolutionData(value, "change", "verify", ""); }
    private static ProposedResolutionData reference(String value) { return new ProposedResolutionData("root", "change", "verify", value); }

    @Test void textFieldsEnforceCodePointAndByteLimitsAndRequireContent() {
        assertTrue(ProposedResolutionData.valid(root("x".repeat(4_000))));
        assertFalse(ProposedResolutionData.valid(root("x".repeat(4_001))));
        assertTrue(ProposedResolutionData.valid(root("🙂".repeat(4_000))), "4,000 supplementary code points are 16,000 UTF-8 bytes");
        assertFalse(ProposedResolutionData.valid(root("🙂".repeat(4_000) + "x")));
        assertFalse(ProposedResolutionData.valid(root(" \t\n")));
        assertFalse(ProposedResolutionData.valid(new ProposedResolutionData("root", " ", "verify", "")));
        assertFalse(ProposedResolutionData.valid(new ProposedResolutionData("root", "change", "", "")));
        assertTrue(ProposedResolutionData.valid(new ProposedResolutionData("root", "x".repeat(4_000), "x".repeat(4_000), "")));
        assertFalse(ProposedResolutionData.valid(new ProposedResolutionData("root", "x".repeat(4_001), "verify", "")));
        assertFalse(ProposedResolutionData.valid(new ProposedResolutionData("root", "change", "x".repeat(4_001), "")));
        assertFalse(ProposedResolutionData.valid(root(null)));
        assertFalse(ProposedResolutionData.valid(null));
    }

    @Test void fixReferenceIsOptionalButBounded() {
        assertTrue(ProposedResolutionData.valid(reference("")));
        assertTrue(ProposedResolutionData.valid(reference("x".repeat(200))));
        assertTrue(ProposedResolutionData.valid(reference("🙂".repeat(200))));
        assertFalse(ProposedResolutionData.valid(reference("x".repeat(201))));
        assertFalse(ProposedResolutionData.valid(reference(null)));
    }

    @Test void controlsAndUnpairedSurrogatesAreRejectedWhileLayoutWhitespaceIsKept() {
        assertTrue(ProposedResolutionData.valid(root("tab\tline\ncarriage\rreturn")));
        assertFalse(ProposedResolutionData.valid(root("nul\u0000")));
        assertFalse(ProposedResolutionData.valid(root("c1\u0085")));
        assertFalse(ProposedResolutionData.valid(root("lone\uD83D")));
        assertFalse(ProposedResolutionData.valid(reference("vertical\u000b")));
    }
}
