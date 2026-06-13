package io.casehub.platform.api.acl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AccessDeniedExceptionTest {

    @Test
    void messageContainsAllFields() {
        var ex = new AccessDeniedException("user1", "case:abc-123", AclAction.READ);

        assertTrue(ex.getMessage().contains("user1"));
        assertTrue(ex.getMessage().contains("case:abc-123"));
        assertTrue(ex.getMessage().contains("READ"));
    }

    @Test
    void accessorsReturnConstructorValues() {
        var ex = new AccessDeniedException("user1", "case:abc-123", AclAction.ADMIN);

        assertEquals("user1", ex.actorId());
        assertEquals("case:abc-123", ex.resourceId());
        assertEquals(AclAction.ADMIN, ex.action());
    }

    @Test
    void isSecurityException() {
        var ex = new AccessDeniedException("u", "r", AclAction.WRITE);
        assertInstanceOf(SecurityException.class, ex);
    }
}
