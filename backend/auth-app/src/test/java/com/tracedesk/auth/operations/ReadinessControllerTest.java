package com.tracedesk.auth.operations;

import com.tracedesk.auth.ai.configuration.SoclaasEnablementManager;
import com.tracedesk.testing.IsolatedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class ReadinessControllerTest {
    @Test void readinessChecksRealSchemaButDisabledAiDoesNotTakeCoreOffline() throws Exception {
        try (var db = IsolatedPostgres.open(System.getenv())) {
            Flyway.configure().dataSource(db).locations("classpath:db/migration").load().migrate();
            var sql = new JdbcTemplate(db);
            var ai = mock(SoclaasEnablementManager.class);
            when(ai.status()).thenReturn(SoclaasEnablementManager.Status.DISABLED);
            var mvc = standaloneSetup(new ReadinessController(sql, ai)).build();
            mvc.perform(get("/health/live")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
            mvc.perform(get("/health/ready")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("READY")).andExpect(jsonPath("$.ai").value("DISABLED"));
            verify(ai).status(); verifyNoMoreInteractions(ai);
            // This owned disposable schema is the only schema changed by the fixture.
            sql.execute("alter table td_ai_accounting_events rename to unavailable_events");
            mvc.perform(get("/health/ready")).andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.status").value("UNAVAILABLE"));
            verifyNoMoreInteractions(ai);
            assertEquals(0, sql.queryForObject("select count(*) from td_ai_tickets", Integer.class));
        }
    }
}
