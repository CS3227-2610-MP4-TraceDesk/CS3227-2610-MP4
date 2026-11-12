package com.tracedesk.auth.reports.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class QaEvaluationApprovalTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-12T00:00:00Z"), ZoneOffset.UTC);
    private static final String REVISION = "a".repeat(40);
    private static final String MODEL = "synthetic-qa-model";
    private static final Instant SPONSORED_UNTIL = CLOCK.instant().plusSeconds(3600);
    private static final Instant APPROVAL_EXPIRY = CLOCK.instant().plusSeconds(1800);

    @Test
    void qaEvaluationRequiresItsOwnExplicitOptIn() {
        assertThrows(IllegalArgumentException.class, () -> QaEvaluationApproval.requireOptIn(
                Map.of("AI_ENABLED", "true", "TRACEDESK_AI_LIVE_CONFIRM", "synthetic-only-24")));
        assertThrows(IllegalArgumentException.class, () -> QaEvaluationApproval.requireOptIn(
                Map.of("TRACEDESK_QA_LIVE_CONFIRM", "qa-synthetic-only-4")));

        assertDoesNotThrow(() -> QaEvaluationApproval.requireOptIn(Map.of(
                "AI_ENABLED", "true", "TRACEDESK_QA_LIVE_CONFIRM", "qa-synthetic-only-4")));
    }

    @Test
    void approvalMustMatchExactFieldsModelRevisionExpiryAndFiniteAllowance() {
        assertEquals(APPROVAL_EXPIRY, QaEvaluationApproval.validate(validApproval().getBytes(java.nio.charset.StandardCharsets.UTF_8), REVISION, MODEL,
                SPONSORED_UNTIL, QaEvaluationApproval.MAX_UNITS, QaEvaluationApproval.MAX_UNITS, CLOCK));

        assertRejected(validApproval().replace("\"approved\":true", "\"approved\":true,\"unexpected\":1"),
                REVISION, MODEL, SPONSORED_UNTIL, QaEvaluationApproval.MAX_UNITS, QaEvaluationApproval.MAX_UNITS);
        assertRejected(validApproval().replace(",\"stopOnFailure\":true", ""),
                REVISION, MODEL, SPONSORED_UNTIL, QaEvaluationApproval.MAX_UNITS, QaEvaluationApproval.MAX_UNITS);
        assertRejected(validApproval().replace("\"approved\":true", "\"approved\":false"),
                REVISION, MODEL, SPONSORED_UNTIL, QaEvaluationApproval.MAX_UNITS, QaEvaluationApproval.MAX_UNITS);
        assertRejected(validApproval().replace("\"maxAttempts\":4", "\"maxAttempts\":5"),
                REVISION, MODEL, SPONSORED_UNTIL, QaEvaluationApproval.MAX_UNITS, QaEvaluationApproval.MAX_UNITS);
        assertRejected(validApproval().replace("QAL-02", "QAL-03"),
                REVISION, MODEL, SPONSORED_UNTIL, QaEvaluationApproval.MAX_UNITS, QaEvaluationApproval.MAX_UNITS);
        assertRejected(validApproval().replace("\"stopOnFailure\":true", "\"stopOnFailure\":false"),
                REVISION, MODEL, SPONSORED_UNTIL, QaEvaluationApproval.MAX_UNITS, QaEvaluationApproval.MAX_UNITS);
        assertRejected(validApproval(), REVISION, "different-synthetic-model", SPONSORED_UNTIL,
                QaEvaluationApproval.MAX_UNITS, QaEvaluationApproval.MAX_UNITS);
        assertRejected(validApproval(), "b".repeat(40), MODEL, SPONSORED_UNTIL,
                QaEvaluationApproval.MAX_UNITS, QaEvaluationApproval.MAX_UNITS);
        assertRejected(validApproval().replace("2026-11-12T00:30:00Z", "2026-11-11T23:59:59Z"),
                REVISION, MODEL, SPONSORED_UNTIL, QaEvaluationApproval.MAX_UNITS, QaEvaluationApproval.MAX_UNITS);
        assertRejected(validApproval(), REVISION, MODEL, APPROVAL_EXPIRY.minusSeconds(1),
                QaEvaluationApproval.MAX_UNITS, QaEvaluationApproval.MAX_UNITS);
        assertRejected(validApproval(), REVISION, MODEL, SPONSORED_UNTIL,
                QaEvaluationApproval.MAX_UNITS + 1, QaEvaluationApproval.MAX_UNITS);
        assertRejected(validApproval(), REVISION, MODEL, SPONSORED_UNTIL,
                QaEvaluationApproval.MAX_UNITS, Long.MAX_VALUE);
        assertRejected(validApproval().replace("\"maxResourceUnits\":36800",
                        "\"maxResourceUnits\":9223372036854775808"),
                REVISION, MODEL, SPONSORED_UNTIL, QaEvaluationApproval.MAX_UNITS, QaEvaluationApproval.MAX_UNITS);
    }

    @Test
    void approvalCanBeClaimedOnlyOnceAcrossConcurrentAttempts(@TempDir Path directory) throws Exception {
        Path approval = directory.resolve("synthetic-approval.json");
        Path receiptA = directory.resolve("claim-a.receipt");
        Path receiptB = directory.resolve("claim-b.receipt");
        byte[] approvedBytes = validApproval().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(approval, approvedBytes);

        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var successes = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> tryClaim(approval, receiptA, approvedBytes, ready, start, successes));
            Future<?> second = executor.submit(() -> tryClaim(approval, receiptB, approvedBytes, ready, start, successes));

            assertTrue(ready.await(5, TimeUnit.SECONDS), "both synthetic claim attempts should be ready");
            start.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);

            assertEquals(1, successes.get());
            assertFalse(Files.exists(approval));
            int receiptCount = (Files.exists(receiptA) ? 1 : 0) + (Files.exists(receiptB) ? 1 : 0);
            assertEquals(1, receiptCount);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void claimRejectsBytesThatDifferFromTheValidatedApproval(@TempDir Path directory) throws IOException {
        Path approval = directory.resolve("synthetic-approval.json");
        Path receipt = directory.resolve("claimed.receipt");
        byte[] onDiskBytes = "{\"approved\":false}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] previouslyValidatedBytes = validApproval().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(approval, onDiskBytes);

        assertThrows(IllegalArgumentException.class,
                () -> QaEvaluationApproval.consume(approval, receipt, previouslyValidatedBytes));
        assertFalse(Files.exists(approval));
        assertArrayEquals(onDiskBytes, Files.readAllBytes(receipt));
    }

    private static void tryClaim(Path approval, Path receipt, byte[] bytes, CountDownLatch ready,
                                 CountDownLatch start, AtomicInteger successes) {
        ready.countDown();
        try {
            if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("claim start timed out");
            QaEvaluationApproval.consume(approval, receipt, bytes);
            successes.incrementAndGet();
        } catch (IOException | IllegalArgumentException expectedForLostOrRejectedClaim) {
            // A moved or mismatched approval is unavailable to this synthetic attempt.
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("claim attempt was interrupted", interrupted);
        }
    }

    private static void assertRejected(String approval, String revision, String model, Instant sponsoredUntil,
                                       long actorQuota, long projectQuota) {
        assertThrows(IllegalArgumentException.class, () -> QaEvaluationApproval.validate(
                approval.getBytes(java.nio.charset.StandardCharsets.UTF_8), revision, model, sponsoredUntil,
                actorQuota, projectQuota, CLOCK));
    }

    private static String validApproval() {
        return """
                {"approved":true,"revision":"%s","model":"%s","expiresAt":"2026-11-12T00:30:00Z",
                 "maxAttempts":4,"maxResourceUnits":36800,"caseIds":["QAL-01","QAL-02"],"stopOnFailure":true}
                """.formatted(REVISION, MODEL);
    }
}
