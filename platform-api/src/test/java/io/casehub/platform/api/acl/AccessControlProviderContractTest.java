package io.casehub.platform.api.acl;

import io.casehub.platform.api.identity.GroupMembershipProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public abstract class AccessControlProviderContractTest {

    protected abstract AccessControlProvider provider();

    protected abstract GroupMembershipProvider groupMembership();

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
        assertEquals(2, readable.size());
        assertTrue(readable.contains("case:abc"));
        assertTrue(readable.contains("case:def"));
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
}
