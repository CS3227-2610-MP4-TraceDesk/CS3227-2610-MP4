package com.tracedesk.auth.investigation.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Pure validation and canonical digest rules for private investigation notes. */
public record InvestigationNoteData(String body) {
    public static boolean valid(InvestigationNoteData data) {
        if (data == null || data.body == null || hasUnpairedSurrogate(data.body)) return false;
        if (data.body.codePointCount(0, data.body.length()) > 8_000) return false;
        boolean nonblank = false;
        for (int offset = 0; offset < data.body.length();) {
            int cp = data.body.codePointAt(offset);
            if ((cp <= 0x1f || (cp >= 0x7f && cp <= 0x9f)) && cp != '\t' && cp != '\n' && cp != '\r') return false;
            if (!Character.isWhitespace(cp) && !Character.isSpaceChar(cp)) nonblank = true;
            offset += Character.charCount(cp);
        }
        return nonblank;
    }

    public byte[] sha256() {
        try { return MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static boolean hasUnpairedSurrogate(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (i + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(++i))) return true;
            } else if (Character.isLowSurrogate(c)) return true;
        }
        return false;
    }
}
