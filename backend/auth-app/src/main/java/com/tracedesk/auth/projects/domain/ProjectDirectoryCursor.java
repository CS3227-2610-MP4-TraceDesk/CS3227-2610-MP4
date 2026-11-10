package com.tracedesk.auth.projects.domain;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

/** Opaque integrity-protected keyset cursor signed by the configured shared secret. */
public final class ProjectDirectoryCursor {
    private final byte[] key;
    public ProjectDirectoryCursor(String secret) {
        if(secret==null||secret.getBytes(StandardCharsets.UTF_8).length<32)
            throw new IllegalArgumentException("Project cursor secret must contain at least 32 UTF-8 bytes");
        this.key=secret.getBytes(StandardCharsets.UTF_8).clone();
    }
    public String encode(String normalizedName, UUID id) {
        String payload = "v1\n" + normalizedName + "\n" + id;
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes) + "." +
                Base64.getUrlEncoder().withoutPadding().encodeToString(sign(bytes));
    }
    public Position decode(String cursor) {
        try {
            if (cursor == null || cursor.length() > 1024) throw new IllegalArgumentException();
            String[] parts = cursor.split("\\.", -1);
            if (parts.length != 2) throw new IllegalArgumentException();
            byte[] payload = Base64.getUrlDecoder().decode(parts[0]);
            byte[] signature = Base64.getUrlDecoder().decode(parts[1]);
            if (!java.security.MessageDigest.isEqual(sign(payload), signature)) throw new IllegalArgumentException();
            String[] values = new String(payload, StandardCharsets.UTF_8).split("\\n", -1);
            if (values.length != 3 || !"v1".equals(values[0])) throw new IllegalArgumentException();
            return new Position(values[1], UUID.fromString(values[2]));
        } catch (RuntimeException failure) { throw new IllegalArgumentException("Invalid cursor"); }
    }
    private byte[] sign(byte[] payload) {
        try { Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(key,"HmacSHA256")); return mac.doFinal(payload); }
        catch (Exception failure) { throw new IllegalStateException(failure); }
    }
    public record Position(String normalizedName, UUID id) { }
}
