package com.tracedesk.local;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Explicit local-only sample identities; never invoked at application startup. */
public final class LocalSampleAccountsSetupCommand {
    private LocalSampleAccountsSetupCommand() { }

    public static void main(String[] args) {
        try {
            for (var result : run(args, System.getenv(), Path.of(".local").toAbsolutePath().normalize())) {
                System.out.println("projectUuid=" + result.projectUuid());
                System.out.println("login=" + result.login());
            }
        } catch (LocalReporterSetupCommand.SafeFailure failure) {
            System.err.println("Local sample account setup failed: " + failure.getMessage());
            System.exit(2);
        } catch (Exception failure) {
            System.err.println("Local sample account setup failed: operation could not be completed safely.");
            System.exit(2);
        }
    }

    static List<LocalReporterSetupCommand.Result> run(String[] args, Map<String, String> env, Path directory) {
        List<LocalReporterSetupCommand.Account> accounts;
        String mode;
        if (args != null && args.length == 1 && "provision".equals(args[0])) {
            mode = "provision";
            accounts = List.of(LocalReporterSetupCommand.Account.values());
        } else if (args != null && args.length == 2 && "reset".equals(args[0])) {
            mode = "reset";
            accounts = List.of(switch (args[1]) {
                case "reporter" -> LocalReporterSetupCommand.Account.REPORTER;
                case "developer" -> LocalReporterSetupCommand.Account.DEVELOPER;
                case "qa" -> LocalReporterSetupCommand.Account.QA;
                default -> throw new LocalReporterSetupCommand.SafeFailure("expected reset reporter, developer or qa");
            });
        } else {
            throw new LocalReporterSetupCommand.SafeFailure("expected provision or reset reporter|developer|qa");
        }
        var results = new ArrayList<LocalReporterSetupCommand.Result>();
        for (var account : accounts) {
            results.add(LocalReporterSetupCommand.run(new String[]{mode}, env, directory.resolve(account.filename), account));
        }
        return List.copyOf(results);
    }
}
