package com.tracedesk.auth.reports.domain;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/** Pure report field rules and contract-ordered canonical request bytes. */
public record ReportData(String title, String description, String environment,
                         String reproductionSteps, String expectedBehavior,
                         String actualBehavior, String textLogs) {
    public static final List<String> FIELD_NAMES = List.of("title", "description", "environment",
            "reproductionSteps", "expectedBehavior", "actualBehavior", "textLogs");
    private static final int[] CODE_POINTS = {160, 10000, 4000, 8000, 4000, 4000, 16000};
    private static final int[] UTF8_BYTES = {640, 40000, 16000, 32000, 16000, 16000, 64000};

    public ReportData {
        textLogs = textLogs == null ? "" : textLogs;
    }

    public static String validate(ReportData data) {
        if (data == null) return "INVALID";
        String[] values = data.values();
        long aggregateBytes = 0;
        for (int i = 0; i < values.length; i++) {
            String value = values[i];
            if (value == null || hasUnpairedSurrogate(value)) return "INVALID";
            int codePoints = value.codePointCount(0, value.length());
            int bytes = value.getBytes(StandardCharsets.UTF_8).length;
            if (codePoints > CODE_POINTS[i] || bytes > UTF8_BYTES[i]) return "INVALID";
            aggregateBytes += bytes;
            for (int offset = 0; offset < value.length();) {
                int cp = value.codePointAt(offset);
                if ((cp <= 0x1f || (cp >= 0x7f && cp <= 0x9f)) && cp != '\t' && cp != '\n' && cp != '\r') return "INVALID";
                offset += Character.charCount(cp);
            }
            if (i < 6 && value.codePoints().noneMatch(cp -> !Character.isWhitespace(cp) && !Character.isSpaceChar(cp))) return "INVALID";
        }
        return aggregateBytes <= 184640 ? null : "INVALID";
    }

    /** Validates the shared field limits while permitting empty draft fields. */
    public static String validateDraft(ReportData data) {
        if (data == null) return "INVALID";
        String[] values = data.values();
        long aggregateBytes = 0;
        for (int i = 0; i < values.length; i++) {
            String value = values[i];
            if (value == null || hasUnpairedSurrogate(value)) return "INVALID";
            int bytes = value.getBytes(StandardCharsets.UTF_8).length;
            if (value.codePointCount(0, value.length()) > CODE_POINTS[i] || bytes > UTF8_BYTES[i]) return "INVALID";
            aggregateBytes += bytes;
            for (int offset = 0; offset < value.length();) {
                int cp = value.codePointAt(offset);
                if ((cp <= 0x1f || (cp >= 0x7f && cp <= 0x9f)) && cp != '\t' && cp != '\n' && cp != '\r') return "INVALID";
                offset += Character.charCount(cp);
            }
        }
        return aggregateBytes <= 184640 ? null : "INVALID";
    }

    public byte[] canonicalJson() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write('{');
        String[] names = FIELD_NAMES.toArray(String[]::new);
        String[] values = values();
        for (int i = 0; i < names.length; i++) {
            if (i > 0) out.write(',');
            writeJsonString(out, names[i]); out.write(':'); writeJsonString(out, values[i]);
        }
        out.write('}');
        return out.toByteArray();
    }

    public static boolean validAssignmentReason(String reason, boolean required) {
        if (reason == null) return !required;
        if (hasUnpairedSurrogate(reason) || reason.codePointCount(0, reason.length()) > 500
                || reason.getBytes(StandardCharsets.UTF_8).length > 2000) return false;
        boolean nonblank = false;
        for (int offset = 0; offset < reason.length();) {
            int cp = reason.codePointAt(offset);
            if (Character.isISOControl(cp) && cp != '\t' && cp != '\n' && cp != '\r') return false;
            if (!Character.isWhitespace(cp) && !Character.isSpaceChar(cp)) nonblank = true;
            offset += Character.charCount(cp);
        }
        return nonblank || !required;
    }

    public byte[] sha256() {
        try { return MessageDigest.getInstance("SHA-256").digest(canonicalJson()); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private String[] values() { return new String[]{title, description, environment, reproductionSteps, expectedBehavior, actualBehavior, textLogs}; }
    private static boolean hasUnpairedSurrogate(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isHighSurrogate(c)) { if (i + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(++i))) return true; }
            else if (Character.isLowSurrogate(c)) return true;
        }
        return false;
    }
    private static void writeJsonString(ByteArrayOutputStream out, String value) {
        out.write('"');
        for (int i = 0; i < value.length();) {
            int cp = value.codePointAt(i);
            String character = new String(Character.toChars(cp));
            switch (cp) {
                case '"' -> writeAscii(out, "\\\""); case '\\' -> writeAscii(out, "\\\\");
                case '\b' -> writeAscii(out, "\\b"); case '\f' -> writeAscii(out, "\\f");
                case '\n' -> writeAscii(out, "\\n"); case '\r' -> writeAscii(out, "\\r"); case '\t' -> writeAscii(out, "\\t");
                default -> { if (cp < 0x20) writeAscii(out, String.format("\\u%04x", cp)); else out.writeBytes(character.getBytes(StandardCharsets.UTF_8)); }
            }
            i += Character.charCount(cp);
        }
        out.write('"');
    }
    private static void writeAscii(ByteArrayOutputStream out, String value) { out.writeBytes(value.getBytes(StandardCharsets.US_ASCII)); }
}
