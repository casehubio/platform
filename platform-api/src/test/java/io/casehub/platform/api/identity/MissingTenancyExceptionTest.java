package io.casehub.platform.api.identity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MissingTenancyExceptionTest {

    @Test
    void message_includes_actorId() {
        var ex = new MissingTenancyException("alice");
        assertEquals("No tenancy identifier for authenticated principal: alice", ex.getMessage());
    }

    @Test
    void actorId_returns_constructor_arg() {
        var ex = new MissingTenancyException("bob");
        assertEquals("bob", ex.actorId());
    }

    @Test
    void extends_RuntimeException() {
        assertInstanceOf(RuntimeException.class, new MissingTenancyException("x"));
    }
}
