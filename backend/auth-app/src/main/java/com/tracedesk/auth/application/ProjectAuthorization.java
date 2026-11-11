package com.tracedesk.auth.application;

import com.tracedesk.auth.domain.ProjectAccessPolicy;
import java.util.Optional;
import java.util.UUID;

/** Applies the project-read policy to fresh facts from the persistence port. */
public final class ProjectAuthorization {
    private final ProjectAccessRepository repository;

    public ProjectAuthorization(ProjectAccessRepository repository) {
        this.repository = repository;
    }

    public Optional<ProjectAccessRepository.Snapshot> readableProject(UUID actorId,
                                                                       UUID projectId) {
        return repository.findProjectAccess(actorId, projectId)
                .filter(snapshot -> ProjectAccessPolicy.mayRead(new ProjectAccessPolicy.Facts(
                        snapshot.userEnabled() && snapshot.accountRole() != null,
                        snapshot.projectEnabled(), snapshot.membershipActive())));
    }
}
