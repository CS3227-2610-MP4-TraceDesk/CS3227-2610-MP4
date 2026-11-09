package com.tracedesk.auth.ai.configuration;

import com.tracedesk.ai.internal.soclaas.SoclaasGatewayFactory;
import com.tracedesk.ai.internal.soclaas.SoclaasSettings;
import com.tracedesk.auth.ai.persistence.JdbcAiLedger;
import java.time.Clock;

/** Test-classpath only: pin one bounded metadata snapshot for a controlled experiment. */
public final class LiveEvaluationManager {
    private LiveEvaluationManager() { }

    public static SoclaasEnablementManager open(SoclaasSettings settings, JdbcAiLedger ledger, Clock clock) {
        var validator = SoclaasGatewayFactory.catalogValidator(settings);
        var probe = new SoclaasEnablementManager.CatalogProbe() {
            @Override public void validateConfiguredModel() { validator.validateConfiguredModel(); }
            @Override public void close() { validator.close(); }
        };
        try { return open(settings, ledger, clock, probe); }
        catch (RuntimeException failure) { probe.close(); throw failure; }
    }

    static SoclaasEnablementManager open(SoclaasSettings settings, JdbcAiLedger ledger, Clock clock,
                                         SoclaasEnablementManager.CatalogProbe probe) {
        return new SoclaasEnablementManager(settings, ledger, clock, probe, false);
    }
}
