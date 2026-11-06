package com.tracedesk.auth.domain;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;

/** Framework-independent bounds and normalization rules for local login identifiers. */
public final class LoginPolicy {
    private LoginPolicy() { }
    public static String normalize(String input) {
        if (input == null) return null;
        String value = Normalizer.normalize(input.trim(), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 3 || bytes.length > 254 || value.chars().anyMatch(c -> Character.isISOControl(c) || Character.isWhitespace(c))) return null;
        int at = value.indexOf('@');
        if (at < 1 || at != value.lastIndexOf('@') || at == value.length() - 1) return null;
        return value;
    }
    public static boolean validPassword(String password) {
        return password != null && password.codePointCount(0, password.length()) >= 12
                && password.codePointCount(0, password.length()) <= 128
                && password.getBytes(StandardCharsets.UTF_8).length <= 512;
    }
}
