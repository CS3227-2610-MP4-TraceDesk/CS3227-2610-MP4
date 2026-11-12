package com.tracedesk.auth.projects.domain;

import java.text.Normalizer;

public record ProjectName(String value) {
    public static ProjectName parse(String input) {
        if (input == null) throw new IllegalArgumentException();
        int start=0,end=input.length();
        while(start<end){int cp=input.codePointAt(start);if(!space(cp))break;start+=Character.charCount(cp);}
        while(end>start){int cp=input.codePointBefore(end);if(!space(cp))break;end-=Character.charCount(cp);}
        String normalized = Normalizer.normalize(input.substring(start,end), Normalizer.Form.NFC);
        int points = normalized.codePointCount(0, normalized.length());
        if (points < 1 || points > 120 || normalized.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 480
                || normalized.codePoints().anyMatch(Character::isISOControl)) throw new IllegalArgumentException();
        return new ProjectName(normalized);
    }
    private static boolean space(int codePoint){return Character.isWhitespace(codePoint)||Character.isSpaceChar(codePoint);}
}
