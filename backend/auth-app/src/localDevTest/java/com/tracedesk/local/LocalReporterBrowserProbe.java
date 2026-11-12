package com.tracedesk.local;

import java.sql.DriverManager;
import java.util.UUID;

/** Disposable browser fixture only; never packaged in the application or local setup tool. */
public final class LocalReporterBrowserProbe {
    public static void main(String[] args) {
        try {
            var env=System.getenv();
            if(!"true".equals(env.get("TRACEDESK_LOCAL_SMOKE_CONFIRM_DISPOSABLE"))
                    || !"true".equals(env.get("TRACEDESK_LOCAL_CONFIRM"))
                    || !"jdbc:postgresql://127.0.0.1:55419/tracedesk_dev".equals(env.get("TRACEDESK_LOCAL_JDBC_URL"))
                    || !"tracedesk_dev".equals(env.get("TRACEDESK_LOCAL_DATABASE_USER"))
                    || args.length!=2 || !(args[0].equals("create") || args[0].equals("remove"))) throw new IllegalStateException();
            UUID id=UUID.fromString(args[1]);
            if(!id.toString().equals(args[1])) throw new IllegalStateException();
            try(var c=DriverManager.getConnection(env.get("TRACEDESK_LOCAL_JDBC_URL"),
                    env.get("TRACEDESK_LOCAL_DATABASE_USER"),env.get("TRACEDESK_LOCAL_DATABASE_PASSWORD"))) {
                try(var s=c.createStatement();var r=s.executeQuery("select current_database(),current_user,session_user,current_schema()")) {
                    if(!r.next() || !"tracedesk_dev".equals(r.getString(1)) || !"tracedesk_dev".equals(r.getString(2))
                        || !"tracedesk_dev".equals(r.getString(3)) || !"public".equals(r.getString(4))) throw new IllegalStateException();
                }
                String sql=args[0].equals("create")
                    ? "insert into td_projects(id,name) values(?,'Local setup access-denial probe')"
                    : "delete from td_projects where id=? and name='Local setup access-denial probe' and not exists(select 1 from td_project_memberships where project_id=?)";
                try(var p=c.prepareStatement(sql)) {
                    p.setObject(1,id);
                    if(args[0].equals("remove")) p.setObject(2,id);
                    if(p.executeUpdate()!=1) throw new IllegalStateException();
                }
            }
        } catch(Exception failure) {
            System.err.println("Disposable local browser probe failed safely.");
            System.exit(2);
        }
    }
}
