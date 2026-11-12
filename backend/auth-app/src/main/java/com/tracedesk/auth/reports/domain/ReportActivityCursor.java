package com.tracedesk.auth.reports.domain;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/** Process-local signed cursor for Reporter activity pages. */
public final class ReportActivityCursor {
    private static final String EVENT_FILTER="v2:REPORT_CREATED,REPORT_EDITED,DRAFT_SUBMITTED,REPORT_INVESTIGATION_STARTED,REPORT_RESOLUTION_PROPOSED,QA_VERIFICATION_SAVED,QA_VERIFICATION_CLOSED,QA_VERIFICATION_SENT_BACK";
    private static final String SORT="occurred_at:asc,id:asc";
    public record Position(UUID actor, UUID project, UUID report, Instant occurredAt, long eventId, long issuedAt) { }
    private final byte[] key = new byte[32];
    public ReportActivityCursor() { new java.security.SecureRandom().nextBytes(key); }

    public String issue(Position p) {
        String raw = String.join("|", "REPORT_ACTIVITY", p.actor().toString(), p.project().toString(),
                p.report().toString(), EVENT_FILTER, SORT, p.occurredAt().toString(),
                Long.toString(p.eventId()), Long.toString(p.issuedAt()));
        byte[] bytes = raw.getBytes(StandardCharsets.UTF_8);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes) + "." +
                Base64.getUrlEncoder().withoutPadding().encodeToString(mac(bytes));
    }

    public Position verify(String token, UUID actor, UUID project, UUID report, Instant now) {
        try {
            if (token == null || token.length() > 4096) return null;
            String[] parts = token.split("\\.", -1);
            if (parts.length != 2) return null;
            var decoder = Base64.getUrlDecoder(); var encoder = Base64.getUrlEncoder().withoutPadding();
            byte[] raw = decoder.decode(parts[0]), signature = decoder.decode(parts[1]);
            if (!encoder.encodeToString(raw).equals(parts[0]) || !encoder.encodeToString(signature).equals(parts[1]) ||
                    !java.security.MessageDigest.isEqual(mac(raw), signature)) return null;
            String[] fields = new String(raw, StandardCharsets.UTF_8).split("\\|", -1);
            if (fields.length != 9 || !fields[0].equals("REPORT_ACTIVITY") || !fields[4].equals(EVENT_FILTER) || !fields[5].equals(SORT)) return null;
            UUID tokenActor=UUID.fromString(fields[1]), tokenProject=UUID.fromString(fields[2]), tokenReport=UUID.fromString(fields[3]);
            Instant occurredAt=Instant.parse(fields[6]);
            if (!occurredAt.toString().equals(fields[6]) || !fields[7].matches("[1-9][0-9]{0,18}")) return null;
            long id=Long.parseLong(fields[7]), issued=Long.parseLong(fields[8]);
            long age=now.toEpochMilli()-issued;
            if (!tokenActor.equals(actor) || !tokenProject.equals(project) || !tokenReport.equals(report) || age < 0 || age >= 900_000) return null;
            return new Position(tokenActor,tokenProject,tokenReport,occurredAt,id,issued);
        } catch (RuntimeException failure) { return null; }
    }

    private byte[] mac(byte[] value) {
        try { Mac mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(key,"HmacSHA256")); return mac.doFinal(value); }
        catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }
}
