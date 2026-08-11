package io.casehub.platform.api.acl;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class WorkerActionTest {

    @Test
    void constructsWithNameAndAclAction() {
        var action = new WorkerAction("READ_CONTEXT", AclAction.READ);
        assertEquals("READ_CONTEXT", action.name());
        assertEquals(AclAction.READ, action.aclAction());
    }

    @Test
    void equalityByValue() {
        assertEquals(
            new WorkerAction("READ_CONTEXT", AclAction.READ),
            new WorkerAction("READ_CONTEXT", AclAction.READ));
    }

    @Test
    void differentNameNotEqual() {
        assertNotEquals(
            new WorkerAction("READ_CONTEXT", AclAction.READ),
            new WorkerAction("WRITE_CONTEXT", AclAction.READ));
    }

    @Test
    void differentAclActionNotEqual() {
        assertNotEquals(
            new WorkerAction("READ_CONTEXT", AclAction.READ),
            new WorkerAction("READ_CONTEXT", AclAction.WRITE));
    }

    @Test
    void rejectsBlankName() {
        assertThrows(IllegalArgumentException.class,
            () -> new WorkerAction("", AclAction.READ));
    }

    @Test
    void rejectsNullName() {
        assertThrows(IllegalArgumentException.class,
            () -> new WorkerAction(null, AclAction.READ));
    }

    @Test
    void rejectsNullAclAction() {
        assertThrows(IllegalArgumentException.class,
            () -> new WorkerAction("READ_CONTEXT", null));
    }
}
