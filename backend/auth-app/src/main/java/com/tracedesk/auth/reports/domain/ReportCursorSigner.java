package com.tracedesk.auth.reports.domain;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/** Process-local HMAC cursor. Tokens expire after 15 minutes and restart invalidates them. */
public final class ReportCursorSigner {
    public record Cursor(UUID actor, UUID project, String purpose, String filter, Instant updatedAt, UUID lastId, long issuedAt) { }
    private final byte[] key;
    public ReportCursorSigner() { key = new byte[32]; new java.security.SecureRandom().nextBytes(key); }
    public String issue(Cursor cursor) {
        String raw = String.join("|", cursor.actor().toString(), cursor.project().toString(), cursor.purpose(),
                cursor.filter(), cursor.updatedAt().toString(), cursor.lastId().toString(), Long.toString(cursor.issuedAt()));
        byte[] bytes = raw.getBytes(StandardCharsets.UTF_8);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes) + "." +
                Base64.getUrlEncoder().withoutPadding().encodeToString(mac(bytes));
    }
    public Cursor verify(String token, UUID actor, UUID project, String purpose, String filter, Instant now) {
        try {
            if (token == null || token.length() > 4096) return null;
            String[] parts = token.split("\\.", -1); if (parts.length != 2) return null;
            byte[] raw = Base64.getUrlDecoder().decode(parts[0]); byte[] signature = Base64.getUrlDecoder().decode(parts[1]);
            Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
            if (!encoder.encodeToString(raw).equals(parts[0]) || !encoder.encodeToString(signature).equals(parts[1])) return null;
            if (!java.security.MessageDigest.isEqual(mac(raw), signature)) return null;
            String[] f = new String(raw, StandardCharsets.UTF_8).split("\\|", -1); if (f.length != 7) return null;
            Cursor c = new Cursor(UUID.fromString(f[0]), UUID.fromString(f[1]), f[2], f[3], Instant.parse(f[4]), UUID.fromString(f[5]), Long.parseLong(f[6]));
            long age = now.toEpochMilli() - c.issuedAt();
            if (!c.actor().equals(actor) || !c.project().equals(project) || !c.purpose().equals(purpose) || !c.filter().equals(filter) || age < 0 || age >= 900_000) return null;
            return c;
        } catch (RuntimeException e) { return null; }
    }
    private byte[] mac(byte[] value) {
        try { Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(key, "HmacSHA256")); return mac.doFinal(value); }
        catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }
}
