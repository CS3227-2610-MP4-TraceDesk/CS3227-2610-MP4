package com.tracedesk.auth.projects.application;

import java.util.List;
import java.util.UUID;

public interface ProjectRepository {
    record Summary(UUID id, String name, String membership, String sortKey) { }
    record Created(UUID id, String name) { }
    record Page(List<Summary> items, boolean hasMore, String nextCursor) { }
    Page directory(UUID actor, int limit, String afterName, UUID afterId);
    Created create(UUID actor, String normalizedName, UUID key, UUID requestId);
    Created join(UUID actor, UUID project, UUID requestId);
}
