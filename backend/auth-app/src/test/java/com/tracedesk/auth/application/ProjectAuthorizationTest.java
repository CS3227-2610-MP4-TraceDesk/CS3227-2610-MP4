package com.tracedesk.auth.application;

import com.tracedesk.auth.domain.ProjectAccessPolicy;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectAuthorizationTest {
    @Test void onlyEnabledUserProjectAndActiveMembershipCanRead() {
        var repo = mock(ProjectAccessRepository.class);
        UUID actor = UUID.randomUUID(), project = UUID.randomUUID();
        var service = new ProjectAuthorization(repo);
        when(repo.findProjectAccess(actor, project)).thenReturn(Optional.of(
                new ProjectAccessRepository.Snapshot(project, "Synthetic", true, true, true)));
        assertTrue(service.readableProject(actor, project).isPresent());
        when(repo.findProjectAccess(actor, project)).thenReturn(Optional.of(
                new ProjectAccessRepository.Snapshot(project, "Synthetic", true, true, false)));
        assertTrue(service.readableProject(actor, project).isEmpty());
        when(repo.findProjectAccess(actor, project)).thenReturn(Optional.of(
                new ProjectAccessRepository.Snapshot(project, "Synthetic", false, true, true)));
        assertTrue(service.readableProject(actor, project).isEmpty());
        when(repo.findProjectAccess(actor, project)).thenReturn(Optional.of(
                new ProjectAccessRepository.Snapshot(project, "Synthetic", true, false, true)));
        assertTrue(service.readableProject(actor, project).isEmpty());
        verify(repo, times(4)).findProjectAccess(actor, project);
    }

    @Test void policyDoesNotDependOnFrameworkTypes() {
        assertFalse(ProjectAccessPolicy.mayRead(null));
        assertFalse(ProjectAccessPolicy.mayRead(new ProjectAccessPolicy.Facts(true, true, false)));
    }
}
