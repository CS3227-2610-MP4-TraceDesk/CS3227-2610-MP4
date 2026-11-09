package com.tracedesk.auth.investigation.domain;

import java.nio.charset.StandardCharsets;

/** Plain-text rules shared by investigation notes and proposed resolutions. */
final class InvestigationText {
    private InvestigationText() { }

    static boolean valid(String value, int maxCodePoints, int maxUtf8Bytes, boolean required) {
        if (value == null || hasUnpairedSurrogate(value)) return false;
        if (value.codePointCount(0, value.length()) > maxCodePoints) return false;
        if (value.getBytes(StandardCharsets.UTF_8).length > maxUtf8Bytes) return false;
        boolean nonblank = false;
        for (int offset = 0; offset < value.length();) {
            int cp = value.codePointAt(offset);
            if ((cp <= 0x1f || (cp >= 0x7f && cp <= 0x9f)) && cp != '\t' && cp != '\n' && cp != '\r') return false;
            if (!Character.isWhitespace(cp) && !Character.isSpaceChar(cp)) nonblank = true;
            offset += Character.charCount(cp);
        }
        return nonblank || !required;
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
