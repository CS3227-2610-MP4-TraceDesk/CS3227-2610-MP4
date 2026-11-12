package com.tracedesk.auth.application;

import java.util.Optional;
import java.util.UUID;

/** Port for fetching current project and membership facts for a trusted actor. */
public interface ProjectAccessRepository {
    record Snapshot(UUID projectId, String projectName, boolean userEnabled,
                    boolean projectEnabled, boolean membershipActive, String accountRole) {
        public Snapshot(UUID projectId, String projectName, boolean userEnabled,
                        boolean projectEnabled, boolean membershipActive) {
            this(projectId, projectName, userEnabled, projectEnabled, membershipActive, "REPORTER");
        }
    }

    Optional<Snapshot> findProjectAccess(UUID userId, UUID projectId);
}
