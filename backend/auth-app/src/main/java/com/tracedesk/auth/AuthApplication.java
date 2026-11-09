package com.tracedesk.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession;
import org.springframework.context.annotation.Bean;
import com.tracedesk.auth.application.ProjectAccessRepository;
import com.tracedesk.auth.application.ProjectAuthorization;
import com.tracedesk.auth.application.WorkspaceDiscovery;
import com.tracedesk.auth.application.WorkspaceMembershipRepository;
import java.time.Clock;

@SpringBootApplication
@EnableJdbcHttpSession(maxInactiveIntervalInSeconds = 1800)
public class AuthApplication {
    @Bean Clock clock() { return Clock.systemUTC(); }
    @Bean ProjectAuthorization projectAuthorization(ProjectAccessRepository repository) {
        return new ProjectAuthorization(repository);
    }
    @Bean WorkspaceDiscovery workspaceDiscovery(WorkspaceMembershipRepository repository) {
        return new WorkspaceDiscovery(repository);
    }
    public static void main(String[] args) { SpringApplication.run(AuthApplication.class, args); }
}
