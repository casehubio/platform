package io.casehub.platform.api.acl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccessDeniedExceptionTest {

    private static final ResourceId CASE_ABC = ResourceId.parse("case:abc-123");

    @Test
    void messageContainsAllFields() {
        var ex = new AccessDeniedException("user1", CASE_ABC, AclAction.READ);

        assertTrue(ex.getMessage().contains("user1"));
        assertTrue(ex.getMessage().contains("case:abc-123"));
        assertTrue(ex.getMessage().contains("READ"));
    }

    @Test
    void accessorsReturnConstructorValues() {
        var ex = new AccessDeniedException("user1", CASE_ABC, AclAction.ADMIN);

        assertEquals("user1", ex.actorId());
        assertEquals(CASE_ABC, ex.resourceId());
        assertEquals(AclAction.ADMIN, ex.action());
    }

    @Test
    void isSecurityException() {
        var ex = new AccessDeniedException("u", ResourceId.parse("r:1"), AclAction.WRITE);
        assertInstanceOf(SecurityException.class, ex);
    }
}
