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
import com.tracedesk.auth.projects.application.ProjectService;
import com.tracedesk.auth.projects.application.ProjectRepository;
import com.tracedesk.auth.projects.domain.ProjectDirectoryCursor;

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
    @Bean ProjectDirectoryCursor projectDirectoryCursor(@org.springframework.beans.factory.annotation.Value("${tracedesk.project-cursor.secret}") String secret){return new ProjectDirectoryCursor(secret);}
    @Bean ProjectService projectService(ProjectRepository repository,ProjectDirectoryCursor cursor){return new ProjectService(repository,cursor);}
    public static void main(String[] args) { SpringApplication.run(AuthApplication.class, args); }
}
