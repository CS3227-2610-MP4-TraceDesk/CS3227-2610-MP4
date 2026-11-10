package com.tracedesk.auth.projects.application;

import com.tracedesk.auth.projects.domain.ProjectDirectoryCursor;
import com.tracedesk.auth.projects.domain.ProjectName;
import java.util.UUID;

public final class ProjectService {
    public static final int DEFAULT_LIMIT=50, MAX_LIMIT=50;
    private final ProjectRepository repository;
    private final ProjectDirectoryCursor cursors;
    public ProjectService(ProjectRepository repository, ProjectDirectoryCursor cursors) { this.repository=repository; this.cursors=cursors; }
    public ProjectRepository.Created create(UUID actor, String name, UUID key, UUID requestId) {
        try { return repository.create(actor, ProjectName.parse(name).value(), key, requestId); }
        catch (IllegalArgumentException failure) { throw new Failure("INVALID_REQUEST",400); }
    }
    public ProjectRepository.Page directory(UUID actor, Integer requestedLimit, String cursor) {
        int limit=requestedLimit==null?DEFAULT_LIMIT:requestedLimit;
        if(limit<1||limit>MAX_LIMIT) throw new Failure("INVALID_REQUEST",400);
        String afterName=null; UUID afterId=null;
        if(cursor!=null) try { var position=cursors.decode(cursor); afterName=position.normalizedName(); afterId=position.id(); }
        catch(IllegalArgumentException failure) { throw new Failure("INVALID_REQUEST",400); }
        var page=repository.directory(actor,limit,afterName,afterId);
        String next=null;
        if(page.hasMore()&&!page.items().isEmpty()) {
            var last=page.items().get(page.items().size()-1);
            next=cursors.encode(last.sortKey(),last.id());
        }
        return new ProjectRepository.Page(page.items(),page.hasMore(),next);
    }
    public ProjectRepository.Created join(UUID actor, UUID project, UUID requestId) { return repository.join(actor,project,requestId); }
    public static final class Failure extends RuntimeException {
        private final int status;
        public Failure(String code,int status){super(code);this.status=status;}
        public int status(){return status;}
        public String code(){return getMessage();}
    }
}
