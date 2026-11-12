package com.tracedesk.auth.reports.ai;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

final class QaEvaluationApproval {
    static final int ATTEMPTS = 4;
    static final long MAX_UNITS = ATTEMPTS * 9200L;
    static final List<String> CASE_IDS = List.of("QAL-01", "QAL-02");
    private static final int MAX_BYTES = 8192;
    private static final Set<String> FIELDS = Set.of("approved", "revision", "model", "expiresAt",
            "maxAttempts", "maxResourceUnits", "caseIds", "stopOnFailure");

    private QaEvaluationApproval() { }

    static void requireOptIn(Map<String, String> environment) {
        if (!"qa-synthetic-only-4".equals(environment.get("TRACEDESK_QA_LIVE_CONFIRM"))
                || !"true".equals(environment.get("AI_ENABLED"))) throw invalid();
    }

    static Instant validate(byte[] bytes, String revision, String model, Instant sponsoredUntil,
                            long actorQuota, long globalQuota, Clock clock) {
        try {
            if (bytes.length > MAX_BYTES || !revision.matches("[a-f0-9]{40}")
                    || actorQuota != MAX_UNITS || globalQuota != MAX_UNITS) throw invalid();
            var record = ReportAiOutput.JSON.readTree(bytes);
            if (record == null || !record.isObject() || !record.propertyNames().equals(FIELDS)
                    || !record.path("approved").isBoolean() || !record.path("approved").asBoolean()
                    || !record.path("stopOnFailure").isBoolean() || !record.path("stopOnFailure").asBoolean()
                    || !record.path("revision").isString() || !revision.equals(record.path("revision").asString())
                    || !record.path("model").isString() || !model.equals(record.path("model").asString())
                    || !record.path("maxAttempts").isInt() || record.path("maxAttempts").asInt() != ATTEMPTS
                    || !record.path("maxResourceUnits").isInt() || record.path("maxResourceUnits").asLong() != MAX_UNITS)
                throw invalid();
            var ids = record.path("caseIds");
            if (!ids.isArray() || ids.size() != CASE_IDS.size()) throw invalid();
            for (int index = 0; index < CASE_IDS.size(); index++)
                if (!ids.get(index).isString() || !CASE_IDS.get(index).equals(ids.get(index).asString())) throw invalid();
            if (!record.path("expiresAt").isString()) throw invalid();
            Instant expiry = Instant.parse(record.path("expiresAt").asString());
            if (!expiry.isAfter(clock.instant()) || expiry.isAfter(sponsoredUntil)) throw invalid();
            return expiry;
        } catch (RuntimeException failure) {
            throw invalid();
        }
    }

    static byte[] read(Path path) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw invalid();
        try (var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            return input.readNBytes(MAX_BYTES + 1);
        }
    }

    static void consume(Path approval, Path receipt, byte[] validatedBytes) throws IOException {
        Files.move(approval, receipt, StandardCopyOption.ATOMIC_MOVE);
        if (!Arrays.equals(validatedBytes, read(receipt))) throw invalid();
    }

    static String cleanRevision(Path root) throws IOException, InterruptedException {
        if (!git(root, "status", "--porcelain", "--untracked-files=normal").isBlank()) throw invalid();
        String revision = git(root, "rev-parse", "HEAD").strip();
        if (!revision.matches("[a-f0-9]{40}")) throw invalid();
        return revision;
    }

    private static String git(Path root, String... arguments) throws IOException, InterruptedException {
        var command = new java.util.ArrayList<>(List.of("git"));
        command.addAll(List.of(arguments));
        var process = new ProcessBuilder(command).directory(root.toFile())
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        try {
            if (!process.waitFor(8, TimeUnit.SECONDS) || process.exitValue() != 0) throw invalid();
            try (var output = process.getInputStream()) {
                return new String(output.readNBytes(1024), java.nio.charset.StandardCharsets.UTF_8);
            }
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("A fresh bounded QA evaluation approval and clean candidate are required");
    }
}
