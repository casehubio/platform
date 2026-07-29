package io.casehub.platform.api.acl;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AclActionTest {

    @Test
    void read_satisfiedBy_includesReadWriteAdmin() {
        Set<AclAction> set = AclAction.READ.satisfiedBy();
        assertEquals(3, set.size());
        assertTrue(set.contains(AclAction.READ));
        assertTrue(set.contains(AclAction.WRITE));
        assertTrue(set.contains(AclAction.ADMIN));
        assertFalse(set.contains(AclAction.CLAIM));
    }

    @Test
    void write_satisfiedBy_includesWriteAdmin() {
        Set<AclAction> set = AclAction.WRITE.satisfiedBy();
        assertEquals(2, set.size());
        assertTrue(set.contains(AclAction.WRITE));
        assertTrue(set.contains(AclAction.ADMIN));
    }

    @Test
    void admin_satisfiedBy_includesOnlyAdmin() {
        assertEquals(Set.of(AclAction.ADMIN), AclAction.ADMIN.satisfiedBy());
    }

    @Test
    void claim_satisfiedBy_includesOnlyClaim() {
        assertEquals(Set.of(AclAction.CLAIM), AclAction.CLAIM.satisfiedBy());
    }

    @Test
    void satisfiedBy_setsAreUnmodifiable() {
        for (AclAction action : AclAction.values()) {
            var set = action.satisfiedBy();
            org.junit.jupiter.api.Assertions.assertThrows(
                    UnsupportedOperationException.class,
                    () -> set.add(AclAction.READ));
        }
    }
}
