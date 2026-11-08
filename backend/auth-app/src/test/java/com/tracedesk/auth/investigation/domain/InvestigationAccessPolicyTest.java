package com.tracedesk.auth.investigation.domain;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class InvestigationAccessPolicyTest {
    @Test void currentAssignmentAndLifecycleBoundEveryCapability() {
        UUID actor = UUID.randomUUID(), other = UUID.randomUUID();
        var open = facts(actor, actor, "DEVELOPER", "OPEN", true);
        var progress = facts(actor, actor, "DEVELOPER", "IN_PROGRESS", true);
        var ready = facts(actor, actor, "DEVELOPER", "READY_FOR_QA", true);
        var closed = facts(actor, actor, "DEVELOPER", "CLOSED", true);
        assertTrue(InvestigationAccessPolicy.mayStart(open));
        assertFalse(InvestigationAccessPolicy.mayMutateNotes(open));
        assertTrue(InvestigationAccessPolicy.mayMutateNotes(progress));
        assertTrue(InvestigationAccessPolicy.mayMutateNotes(ready));
        assertTrue(InvestigationAccessPolicy.mayReadNotes(closed));
        assertFalse(InvestigationAccessPolicy.mayReadNotes(facts(actor, other, "DEVELOPER", "IN_PROGRESS", true)));
        assertFalse(InvestigationAccessPolicy.mayReadNotes(facts(actor, actor, "QA_REVIEWER", "IN_PROGRESS", true)));
        assertFalse(InvestigationAccessPolicy.mayReadNotes(facts(actor, actor, "DEVELOPER", "IN_PROGRESS", false)));
    }
    private static InvestigationAccessPolicy.Facts facts(UUID actor, UUID assigned, String role, String status, boolean active) {
        return new InvestigationAccessPolicy.Facts(true, true, active, role, actor, assigned, status);
    }
}
