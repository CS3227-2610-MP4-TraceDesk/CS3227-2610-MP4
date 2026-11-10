package com.tracedesk.local;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LocalReporterSetupCommandTest {
    @TempDir Path temp;

    @Test void missingConfirmationFailsBeforeCreatingLocalFilesOrConnecting() {
        Path credential=temp.resolve(".local/local-reporter.json");
        var failure=assertThrows(LocalReporterSetupCommand.SafeFailure.class,
                () -> LocalReporterSetupCommand.run(new String[]{"provision"}, Map.of(), credential));
        assertEquals("explicit local confirmation is required",failure.getMessage());
        assertFalse(Files.exists(credential.getParent()));
    }

    @Test void prohibitedProfileAndNonAllowlistedTargetsFailWithoutDatabaseAccess() {
        Path credential=temp.resolve(".local/local-reporter.json");
        Map<String,String> base=new HashMap<>(); base.put("TRACEDESK_LOCAL_CONFIRM","true");
        base.put("TRACEDESK_LOCAL_JDBC_URL","jdbc:postgresql://127.0.0.1:55419/tracedesk_dev");
        base.put("TRACEDESK_LOCAL_DATABASE_USER","tracedesk_dev"); base.put("TRACEDESK_LOCAL_DATABASE_PASSWORD","synthetic-test-only");
        base.put("SPRING_PROFILES_ACTIVE","production");
        assertEquals("a prohibited Spring profile is active",assertThrows(LocalReporterSetupCommand.SafeFailure.class,
                () -> LocalReporterSetupCommand.run(new String[]{"provision"},base,credential)).getMessage());
        base.remove("SPRING_PROFILES_ACTIVE"); base.put("TRACEDESK_LOCAL_JDBC_URL","jdbc:postgresql://example.invalid:55419/tracedesk_dev");
        assertEquals("database target is outside the local allowlist",assertThrows(LocalReporterSetupCommand.SafeFailure.class,
                () -> LocalReporterSetupCommand.run(new String[]{"provision"},base,credential)).getMessage());
    }

    @Test void generatedPasswordMeetsPolicyAndCredentialFileIsOwnerOnly() throws Exception {
        String password=LocalReporterSetupCommand.randomPassword();
        assertTrue(com.tracedesk.auth.domain.LoginPolicy.validPassword(password));
        Path dir=temp.resolve("private"); Files.createDirectory(dir); Files.setPosixFilePermissions(dir,java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
        Path file=dir.resolve("local-reporter.json");
        var writer=new LocalReporterSetupCommand.LocalReporterCredentialFile(file);
        var credential=new LocalReporterSetupCommand.Credential("jdbc:postgresql://127.0.0.1:55419/tracedesk_dev","tracedesk_dev",LocalReporterSetupCommand.LOGIN,password,java.util.UUID.randomUUID());
        writer.install(credential);
        assertEquals(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"),Files.getPosixFilePermissions(file));
        assertTrue(writer.readAndValidate(credential.jdbcUrl(),credential.databaseUser()).password().equals(password));
        assertTrue(Files.readString(file).contains("\"formatVersion\":1"));
        assertFalse(Files.readString(file).contains("userUuid"));
    }
}
