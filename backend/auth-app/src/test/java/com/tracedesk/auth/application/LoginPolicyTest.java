package com.tracedesk.auth.application;
import com.tracedesk.auth.domain.LoginPolicy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LoginPolicyTest {
 @Test void normalizesNfkcTrimAndLocaleRootCase(){ assertEquals("user@example.test",LoginPolicy.normalize("  Ｕser@Example.Test  ")); }
 @Test void rejectsMalformedIdentifiers(){ assertNull(LoginPolicy.normalize("x")); assertNull(LoginPolicy.normalize("a b@x.y")); assertNull(LoginPolicy.normalize("a@@b")); }
 @Test void passwordBoundsCountCodePointsAndBytes(){ assertTrue(LoginPolicy.validPassword("123456789012")); assertFalse(LoginPolicy.validPassword("short")); assertFalse(LoginPolicy.validPassword("a".repeat(513))); }
}
