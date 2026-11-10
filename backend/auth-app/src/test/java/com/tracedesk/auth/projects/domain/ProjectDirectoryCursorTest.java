package com.tracedesk.auth.projects.domain;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ProjectDirectoryCursorTest {
    @Test void cursorIsOpaqueBoundAndTamperEvident() {
        String secret="synthetic-project-cursor-secret-for-tests-0001";
        var signer=new ProjectDirectoryCursor(secret); UUID id=UUID.randomUUID();
        String cursor=signer.encode("alpha",id);
        assertEquals(new ProjectDirectoryCursor.Position("alpha",id),signer.decode(cursor));
        assertThrows(IllegalArgumentException.class,()->signer.decode(cursor.substring(0,cursor.length()-1)+"A"));
        assertThrows(IllegalArgumentException.class,()->new ProjectDirectoryCursor("different-synthetic-project-cursor-secret-0002").decode(cursor));
        assertEquals(new ProjectDirectoryCursor.Position("alpha",id),new ProjectDirectoryCursor(secret).decode(cursor),"replicas with the configured shared secret accept the same cursor");
    }
}
