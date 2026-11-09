package com.tracedesk.auth.application;

import java.util.Objects;
import java.util.UUID;

/** Bounded application service for the authenticated workspace picker. */
public final class WorkspaceDiscovery {
    public static final int MAX_ITEMS = 100;
    private final WorkspaceMembershipRepository repository;

    public WorkspaceDiscovery(WorkspaceMembershipRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    public WorkspaceMembershipRepository.Page discover(UUID actorId) {
        return repository.activeMemberships(Objects.requireNonNull(actorId), MAX_ITEMS);
    }
}
