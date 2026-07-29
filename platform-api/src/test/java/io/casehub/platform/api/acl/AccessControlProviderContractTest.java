package io.casehub.platform.api.acl;

import io.casehub.platform.api.identity.GroupMembershipProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class AccessControlProviderContractTest {

    protected abstract AccessControlProvider provider();

    protected abstract GroupMembershipProvider groupMembership();

    protected abstract String tenancyId();

    protected abstract void setTenancyId(String tenancyId);


    @BeforeEach
    void setUp() {
        clearState();
    }

    protected void clearState() {}

    @Test
    void canAccess_noGrant_returnsFalse() {
        assertFalse(provider().canAccess("actor1", "case:abc", AclAction.READ));
    }

    @Test
    void canAccess_afterGrant_returnsTrue() {
        provider().grant("actor1", "case:abc", AclAction.READ, null);
        assertTrue(provider().canAccess("actor1", "case:abc", AclAction.READ));
    }

    @Test
    void canAccess_differentAction_returnsFalse() {
        provider().grant("actor1", "case:abc", AclAction.READ, null);
        assertFalse(provider().canAccess("actor1", "case:abc", AclAction.WRITE));
    }

    @Test
    void canAccess_differentActor_returnsFalse() {
        provider().grant("actor1", "case:abc", AclAction.READ, null);
        assertFalse(provider().canAccess("actor2", "case:abc", AclAction.READ));
    }

    @Test
    void canAccess_differentResource_returnsFalse() {
        provider().grant("actor1", "case:abc", AclAction.READ, null);
        assertFalse(provider().canAccess("actor1", "case:def", AclAction.READ));
    }

    @Test
    void revoke_removesGrant() {
        provider().grant("actor1", "case:abc", AclAction.READ, null);
        provider().revoke("actor1", "case:abc", AclAction.READ);
        assertFalse(provider().canAccess("actor1", "case:abc", AclAction.READ));
    }

    @Test
    void revokeAll_removesAllActionsForActorAndResource() {
        provider().grant("actor1", "case:abc", AclAction.READ, null);
        provider().grant("actor1", "case:abc", AclAction.WRITE, null);
        provider().revokeAll("actor1", "case:abc");
        assertFalse(provider().canAccess("actor1", "case:abc", AclAction.READ));
        assertFalse(provider().canAccess("actor1", "case:abc", AclAction.WRITE));
    }

    @Test
    void revokeAll_doesNotAffectOtherResources() {
        provider().grant("actor1", "case:abc", AclAction.READ, null);
        provider().grant("actor1", "case:def", AclAction.READ, null);
        provider().revokeAll("actor1", "case:abc");
        assertTrue(provider().canAccess("actor1", "case:def", AclAction.READ));
    }

    @Test
    void canAccess_groupGrant_resolvedViaGroupsOf() {
        provider().grant("group:managers", "case:abc", AclAction.READ, null);
        assertTrue(provider().canAccess("actor1", "case:abc", AclAction.READ));
    }

    @Test
    void canAccess_groupGrant_actorNotInGroup_returnsFalse() {
        provider().grant("group:managers", "case:abc", AclAction.READ, null);
        assertFalse(provider().canAccess("actor-no-groups", "case:abc", AclAction.READ));
    }

    @Test
    void registerParent_childInheritsAccess() {
        provider().grant("actor1", "case:parent", AclAction.READ, null);
        provider().registerParent("planitem:child1", "case:parent");
        assertTrue(provider().canAccess("actor1", "planitem:child1", AclAction.READ));
    }

    @Test
    void registerParent_grandchildInheritsAccess() {
        provider().grant("actor1", "case:root", AclAction.READ, null);
        provider().registerParent("planitem:child", "case:root");
        provider().registerParent("workitem:grandchild", "planitem:child");
        assertTrue(provider().canAccess("actor1", "workitem:grandchild", AclAction.READ));
    }

    @Test
    void registerParent_reparenting_updatesParent() {
        provider().grant("actor1", "case:newParent", AclAction.READ, null);
        provider().registerParent("planitem:child", "case:oldParent");
        provider().registerParent("planitem:child", "case:newParent");
        assertTrue(provider().canAccess("actor1", "planitem:child", AclAction.READ));
    }

    @Test
    void registerParent_noGrantOnParent_returnsFalse() {
        provider().registerParent("planitem:child", "case:parent");
        assertFalse(provider().canAccess("actor1", "planitem:child", AclAction.READ));
    }

    @Test
    void canAccess_expiredGrant_returnsFalse() {
        provider().grant("actor1", "case:abc", AclAction.READ,
                         Instant.now().minus(1, ChronoUnit.HOURS));
        assertFalse(provider().canAccess("actor1", "case:abc", AclAction.READ));
    }

    @Test
    void canAccess_futureExpiry_returnsTrue() {
        provider().grant("actor1", "case:abc", AclAction.READ,
                         Instant.now().plus(1, ChronoUnit.HOURS));
        assertTrue(provider().canAccess("actor1", "case:abc", AclAction.READ));
    }

    @Test
    void accessibleResources_returnsMatchingResources() {
        provider().grant("actor1", "case:abc", AclAction.READ, null);
        provider().grant("actor1", "case:def", AclAction.READ, null);
        provider().grant("actor1", "case:ghi", AclAction.WRITE, null);

        List<String> readable = provider().accessibleResources("actor1",
                                                               AclResourceType.CASE, AclAction.READ);
        assertEquals(3, readable.size());
        assertTrue(readable.contains("case:abc"));
        assertTrue(readable.contains("case:def"));
        assertTrue(readable.contains("case:ghi"));
    }

    @Test
    void accessibleResources_excludesExpired() {
        provider().grant("actor1", "case:abc", AclAction.READ, null);
        provider().grant("actor1", "case:expired", AclAction.READ,
                         Instant.now().minus(1, ChronoUnit.HOURS));

        List<String> readable = provider().accessibleResources("actor1",
                                                               AclResourceType.CASE, AclAction.READ);
        assertEquals(1, readable.size());
        assertTrue(readable.contains("case:abc"));
    }

    @Test
    void accessibleResources_includesGroupGrants() {
        provider().grant("group:managers", "case:abc", AclAction.READ, null);

        List<String> readable = provider().accessibleResources("actor1",
                                                               AclResourceType.CASE, AclAction.READ);
        assertTrue(readable.contains("case:abc"));
    }

    @Test
    void grant_duplicateIsIdempotent() {
        provider().grant("actor1", "case:abc", AclAction.READ, null);
        provider().grant("actor1", "case:abc", AclAction.READ, null);
        assertTrue(provider().canAccess("actor1", "case:abc", AclAction.READ));
    }

    @Test
    void revoke_nonExistentIsNoOp() {
        assertDoesNotThrow(() -> provider().revoke("actor1", "case:abc", AclAction.READ));
    }

    @Test
    void canAccess_adminAction_impliesWriteAndRead() {
        provider().grant("actor1", "case:abc", AclAction.ADMIN, null);
        assertTrue(provider().canAccess("actor1", "case:abc", AclAction.ADMIN));
        assertTrue(provider().canAccess("actor1", "case:abc", AclAction.WRITE));
        assertTrue(provider().canAccess("actor1", "case:abc", AclAction.READ));
        assertFalse(provider().canAccess("actor1", "case:abc", AclAction.CLAIM));
    }

    @Test
    void canAccess_claimAction_isOrthogonal() {
        provider().grant("actor1", "case:abc", AclAction.CLAIM, null);
        assertTrue(provider().canAccess("actor1", "case:abc", AclAction.CLAIM));
        assertFalse(provider().canAccess("actor1", "case:abc", AclAction.READ));
        assertFalse(provider().canAccess("actor1", "case:abc", AclAction.WRITE));
        assertFalse(provider().canAccess("actor1", "case:abc", AclAction.ADMIN));
    }

    @Test
    void canAccess_writeAction_impliesRead() {
        provider().grant("actor1", "case:abc", AclAction.WRITE, null);
        assertTrue(provider().canAccess("actor1", "case:abc", AclAction.WRITE));
        assertTrue(provider().canAccess("actor1", "case:abc", AclAction.READ));
        assertFalse(provider().canAccess("actor1", "case:abc", AclAction.ADMIN));
        assertFalse(provider().canAccess("actor1", "case:abc", AclAction.CLAIM));
    }


    @Test
    void registerParent_depthGuardAt20_returnsFalse() {
        provider().grant("actor1", "case:root", AclAction.READ, null);
        String prev = "case:root";
        for (int i = 1; i <= 21; i++) {
            String child = "res:child" + i;
            provider().registerParent(child, prev);
            prev = child;
        }
        assertFalse(provider().canAccess("actor1", prev, AclAction.READ));
    }

    @Test
    void accessibleResources_planItemType_returnsCorrectResources() {
        provider().grant("actor1", "planitem:pi1", AclAction.READ, null);
        provider().grant("actor1", "case:abc", AclAction.READ, null);

        List<String> result = provider().accessibleResources("actor1",
                                                             AclResourceType.PLAN_ITEM, AclAction.READ);
        assertEquals(1, result.size());
        assertTrue(result.contains("planitem:pi1"));
    }

    @Test
    void accessibleResources_deduplicatesDirectAndGroupGrants() {
        provider().grant("actor1", "case:abc", AclAction.READ, null);
        provider().grant("group:managers", "case:abc", AclAction.READ, null);

        List<String> result = provider().accessibleResources("actor1",
                                                             AclResourceType.CASE, AclAction.READ);
        assertEquals(1, result.size());
        assertTrue(result.contains("case:abc"));
    }

    @Test
    void canAccess_differentTenant_returnsFalse() {
        provider().grant("actor1", "case:abc", AclAction.READ, null);
        setTenancyId("other-tenant");
        assertFalse(provider().canAccess("actor1", "case:abc", AclAction.READ));
        setTenancyId(tenancyId());
    }

    @Test
    void revoke_differentTenant_doesNotDelete() {
        provider().grant("actor1", "case:abc", AclAction.READ, null);
        setTenancyId("other-tenant");
        provider().revoke("actor1", "case:abc", AclAction.READ);
        setTenancyId(tenancyId());
        assertTrue(provider().canAccess("actor1", "case:abc", AclAction.READ));
    }

    @Test
    void accessibleResources_filteredByTenant() {
        provider().grant("actor1", "case:abc", AclAction.READ, null);
        setTenancyId("other-tenant");
        provider().grant("actor1", "case:def", AclAction.READ, null);
        List<String> result = provider().accessibleResources("actor1", AclResourceType.CASE, AclAction.READ);
        assertEquals(1, result.size());
        assertTrue(result.contains("case:def"));
        setTenancyId(tenancyId());
        result = provider().accessibleResources("actor1", AclResourceType.CASE, AclAction.READ);
        assertEquals(1, result.size());
        assertTrue(result.contains("case:abc"));
    }

    @Test
    void grant_sameTupleDifferentTenant_bothStored() {
        provider().grant("actor1", "case:abc", AclAction.READ, null);
        setTenancyId("other-tenant");
        provider().grant("actor1", "case:abc", AclAction.READ, null);
        assertTrue(provider().canAccess("actor1", "case:abc", AclAction.READ));
        setTenancyId(tenancyId());
        assertTrue(provider().canAccess("actor1", "case:abc", AclAction.READ));
    }
}
