package com.tracedesk.auth.operations;

import com.tracedesk.auth.ai.configuration.SoclaasEnablementManager;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

/** Bounded operational states only. Never selects business content or calls the provider. */
@RestController
public final class ReadinessController {
    private final JdbcTemplate sql;
    private final SoclaasEnablementManager ai;

    public ReadinessController(JdbcTemplate applicationSql, SoclaasEnablementManager ai) {
        this.sql = new JdbcTemplate(applicationSql.getDataSource());
        this.sql.setQueryTimeout(2);
        this.ai = ai;
    }

    @GetMapping("/health/live")
    public Map<String, String> live() { return Map.of("status", "UP"); }

    @GetMapping("/health/ready")
    public ResponseEntity<Map<String, String>> ready() {
        try {
            // LIMIT 0 checks required schema and privileges without reading private rows.
            sql.queryForList("select u.id,p.id,m.user_id,s.primary_id,c.policy_revision,c.metadata_available,c.approval_valid_until,t.id,e.ticket_id "
                    + "from td_users u cross join td_projects p cross join td_project_memberships m "
                    + "cross join spring_session s cross join td_ai_control c cross join td_ai_tickets t "
                    + "cross join td_ai_accounting_events e limit 0");
            return ResponseEntity.ok(Map.of("status", "READY", "ai", ai.status().name()));
        } catch (RuntimeException unavailable) {
            return ResponseEntity.status(503).body(Map.of("status", "UNAVAILABLE", "ai", "UNAVAILABLE"));
        }
    }
}
