package com.tracedesk.auth.reports.domain;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ReportAccessPolicyTest {
    @Test void memberAndObjectVisibilityAreRoleAndOwnerBound() {
        UUID reporter = UUID.randomUUID(), other = UUID.randomUUID();
        var own = new ReportAccessPolicy.Facts(true,true,true,"REPORTER",reporter);
        var developer = new ReportAccessPolicy.Facts(true,true,true,"DEVELOPER",other);
        var qa = new ReportAccessPolicy.Facts(true,true,true,"QA_REVIEWER",other);
        assertTrue(ReportAccessPolicy.mayCreate(own));
        assertTrue(ReportAccessPolicy.mayRead(own,reporter));
        assertFalse(ReportAccessPolicy.mayRead(own,other));
        assertTrue(ReportAccessPolicy.mayRead(developer,other));
        assertTrue(ReportAccessPolicy.mayRead(qa,other));
        assertFalse(ReportAccessPolicy.mayCreate(developer));
        assertFalse(ReportAccessPolicy.mayRead(new ReportAccessPolicy.Facts(true,true,false,"QA_REVIEWER",other),other));
        assertFalse(ReportAccessPolicy.mayRead(new ReportAccessPolicy.Facts(false,true,true,"DEVELOPER",other),other));
    }
}
