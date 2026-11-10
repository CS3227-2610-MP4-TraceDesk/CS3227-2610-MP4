package com.tracedesk.auth.investigation.ai;

import com.tracedesk.ai.gateway.*;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static com.tracedesk.ai.gateway.GatewayTypes.*;
import static org.junit.jupiter.api.Assertions.*;

class InvestigationAiA16HiddenTest {
    @Test void a16_secondAuthorizerCallReadsChangedSavedState() throws Exception {
        try(var f=new InvestigationAiAccessTest.Fixture()) {
            var checks=new AtomicInteger();
            try(var service=new InvestigationAiAccess(f.source,InvestigationAiAccessTest.CLOCK,(actors,authorizer,contexts) ->
                new SecuredAiGateway(actors,(actor,request) -> {
                    if(checks.incrementAndGet()==1) {
                        authorizer.authorize(actor,request);
                        f.sql.update("update td_reports set assigned_developer_id=? where id=?",f.second,f.report);
                        // Same resolved actor, request and request-local selection; fresh database authority must win.
                        authorizer.authorize(actor,request);
                        fail("Second authorizer call reused stale authority");
                    }
                },contexts,f.provider,f.ledger,f.policy,InvestigationAiAccessTest.CLOCK))) {
                f.denied("FORBIDDEN",403,() -> service.suggest(f.project,f.report,1,f.selection(),f.live::get));
                assertEquals(1,checks.get()); f.noAdmission();
            }
        }
    }
}
