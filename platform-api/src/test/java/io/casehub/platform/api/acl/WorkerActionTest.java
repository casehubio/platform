package io.casehub.platform.api.acl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class WorkerActionTest {

    @Test
    void readContext_mapsToRead() {
        assertEquals(AclAction.READ, WorkerAction.READ_CONTEXT.aclAction());
    }

    @Test
    void writeContext_mapsToWrite() {
        assertEquals(AclAction.WRITE, WorkerAction.WRITE_CONTEXT.aclAction());
    }

    @Test
    void signalCase_mapsToWrite() {
        assertEquals(AclAction.WRITE, WorkerAction.SIGNAL_CASE.aclAction());
    }

    @Test
    void readEventLog_mapsToRead() {
        assertEquals(AclAction.READ, WorkerAction.READ_EVENT_LOG.aclAction());
    }

    @Test
    void readPlanItems_mapsToRead() {
        assertEquals(AclAction.READ, WorkerAction.READ_PLAN_ITEMS.aclAction());
    }

    @Test
    void spawnSubCase_mapsToWrite() {
        assertEquals(AclAction.WRITE, WorkerAction.SPAWN_SUB_CASE.aclAction());
    }

    @Test
    void claimWorkItem_mapsToClaim() {
        assertEquals(AclAction.CLAIM, WorkerAction.CLAIM_WORK_ITEM.aclAction());
    }

    @Test
    void admin_mapsToAdmin() {
        assertEquals(AclAction.ADMIN, WorkerAction.ADMIN.aclAction());
    }

    @ParameterizedTest
    @EnumSource(WorkerAction.class)
    void everyAction_hasNonNullAclAction(WorkerAction action) {
        assertNotNull(action.aclAction());
    }

    @ParameterizedTest
    @EnumSource(WorkerAction.class)
    void everyAction_aclActionIsSatisfiedByItself(WorkerAction action) {
        assertTrue(action.aclAction().satisfiedBy().contains(action.aclAction()));
    }
}
