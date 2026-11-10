package com.tracedesk.local;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class LocalReporterSafetyTest {
    @TempDir Path temp;

    @Test void rejectsUnsafeTargetsBeforeConnecting() {
        var env=new HashMap<>(Map.of("TRACEDESK_LOCAL_CONFIRM","true",
            "TRACEDESK_LOCAL_DATABASE_USER","tracedesk_dev",
            "TRACEDESK_LOCAL_DATABASE_PASSWORD","synthetic-guard-canary"));
        for(String url:new String[]{"jdbc:postgresql://example.invalid:5432/tracedesk_dev",
            "jdbc:postgresql://127.0.0.1:54329/production","jdbc:postgresql://127.0.0.1:54330/tracedesk_test",
            "jdbc:postgresql://127.0.0.1/tracedesk_dev","jdbc:postgresql://127.0.0.1:0/tracedesk_dev",
            "jdbc:postgresql://127.0.0.1:65536/tracedesk_dev","jdbc:postgresql://localhost:54329/tracedesk_dev?currentSchema=private",
            "jdbc:postgresql://user@localhost:54329/tracedesk_dev","jdbc:postgresql://localhost,example.invalid:54329/tracedesk_dev"}) {
            env.put("TRACEDESK_LOCAL_JDBC_URL",url);
            var error=assertThrows(LocalReporterSetupCommand.SafeFailure.class,
                ()->LocalReporterSetupCommand.run(new String[]{"provision"},env,temp.resolve("private/credentials.json")));
            assertTrue(error.getMessage().contains("allowlist"));
            assertFalse(Files.exists(temp.resolve("private/credentials.json")));
        }
    }

    @Test void refusesSymlinkDirectoryAndDoesNotChangeItsDestination() throws Exception {
        Path real=temp.resolve("real");Files.createDirectory(real);
        Files.setPosixFilePermissions(real,PosixFilePermissions.fromString("rwx------"));
        Path canary=real.resolve("canary");Files.writeString(canary,"synthetic-file-canary");
        Path link=temp.resolve("link");Files.createSymbolicLink(link,real);
        assertThrows(LocalReporterSetupCommand.SafeFailure.class,()->LocalReporterSetupCommand.run(
            new String[]{"provision"},Map.of("TRACEDESK_LOCAL_CONFIRM","true"),link.resolve("credentials.json")));
        assertEquals("synthetic-file-canary",Files.readString(canary));
        assertFalse(Files.exists(real.resolve("credentials.json")));
    }

    @Test void refusesPublicDirectoryWithoutRepairingItsPermissions() throws Exception {
        Path dir=temp.resolve("public");Files.createDirectory(dir);
        var permissions=PosixFilePermissions.fromString("rwxr-xr-x");Files.setPosixFilePermissions(dir,permissions);
        assertThrows(LocalReporterSetupCommand.SafeFailure.class,()->LocalReporterSetupCommand.run(
            new String[]{"provision"},Map.of("TRACEDESK_LOCAL_CONFIRM","true"),dir.resolve("credentials.json")));
        assertEquals(permissions,Files.getPosixFilePermissions(dir));
    }

    @Test void refusesSymlinkCredentialAndLeavesExistingCanaryUntouched() throws Exception {
        Path dir=temp.resolve("private");Files.createDirectory(dir);
        Files.setPosixFilePermissions(dir,PosixFilePermissions.fromString("rwx------"));
        Path canary=temp.resolve("canary");Files.writeString(canary,"synthetic-file-canary");
        Path target=dir.resolve("credentials.json");Files.createSymbolicLink(target,canary);
        var writer=new LocalReporterSetupCommand.LocalReporterCredentialFile(target);
        assertThrows(LocalReporterSetupCommand.SafeFailure.class,()->writer.install(new LocalReporterSetupCommand.Credential(
            "jdbc:postgresql://127.0.0.1:55419/tracedesk_dev","tracedesk_dev",LocalReporterSetupCommand.LOGIN,
            LocalReporterSetupCommand.randomPassword(),UUID.randomUUID())));
        assertEquals("synthetic-file-canary",Files.readString(canary));
        assertTrue(Files.isSymbolicLink(target));
    }
}
