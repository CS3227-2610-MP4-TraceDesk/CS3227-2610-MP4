package com.tracedesk.auth.investigation.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Pure validation and canonical digest rules for private investigation notes. */
public record InvestigationNoteData(String body) {
    public static boolean valid(InvestigationNoteData data) {
        return data != null && InvestigationText.valid(data.body, 8_000, Integer.MAX_VALUE, true);
    }

    public byte[] sha256() {
        try { return MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
