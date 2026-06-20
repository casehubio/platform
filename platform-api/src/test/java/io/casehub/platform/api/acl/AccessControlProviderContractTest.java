package io.casehub.platform.api.acl;

import io.casehub.platform.api.identity.GroupMembershipProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.*;

public abstract class AccessControlProviderContractTest {

    protected abstract AccessControlProvider provider();

    protected abstract GroupMembershipProvider groupMembership();

    @BeforeEach
    void setUp() {
        clearState();
    }

    protected void clearState() {}

    private <T> T await(CompletionStage<T> cs) {
        return cs.toCompletableFuture().join();
    }

    @Test
    void canAccess_noGrant_returnsFalse() {
        assertFalse(await(provider().canAccess("actor1", "case:abc", AclAction.READ)));
    }

    @Test
    void canAccess_afterGrant_returnsTrue() {
        await(provider().grant("actor1", "case:abc", AclAction.READ, null));
        assertTrue(await(provider().canAccess("actor1", "case:abc", AclAction.READ)));
    }

    @Test
    void canAccess_differentAction_returnsFalse() {
        await(provider().grant("actor1", "case:abc", AclAction.READ, null));
        assertFalse(await(provider().canAccess("actor1", "case:abc", AclAction.WRITE)));
    }

    @Test
    void canAccess_differentActor_returnsFalse() {
        await(provider().grant("actor1", "case:abc", AclAction.READ, null));
        assertFalse(await(provider().canAccess("actor2", "case:abc", AclAction.READ)));
    }

    @Test
    void canAccess_differentResource_returnsFalse() {
        await(provider().grant("actor1", "case:abc", AclAction.READ, null));
        assertFalse(await(provider().canAccess("actor1", "case:def", AclAction.READ)));
    }

    @Test
    void revoke_removesGrant() {
        await(provider().grant("actor1", "case:abc", AclAction.READ, null));
        await(provider().revoke("actor1", "case:abc", AclAction.READ));
        assertFalse(await(provider().canAccess("actor1", "case:abc", AclAction.READ)));
    }

    @Test
    void revokeAll_removesAllActionsForActorAndResource() {
        await(provider().grant("actor1", "case:abc", AclAction.READ, null));
        await(provider().grant("actor1", "case:abc", AclAction.WRITE, null));
        await(provider().revokeAll("actor1", "case:abc"));
        assertFalse(await(provider().canAccess("actor1", "case:abc", AclAction.READ)));
        assertFalse(await(provider().canAccess("actor1", "case:abc", AclAction.WRITE)));
    }

    @Test
    void revokeAll_doesNotAffectOtherResources() {
        await(provider().grant("actor1", "case:abc", AclAction.READ, null));
        await(provider().grant("actor1", "case:def", AclAction.READ, null));
        await(provider().revokeAll("actor1", "case:abc"));
        assertTrue(await(provider().canAccess("actor1", "case:def", AclAction.READ)));
    }

    @Test
    void canAccess_groupGrant_resolvedViaGroupsOf() {
        await(provider().grant("group:managers", "case:abc", AclAction.READ, null));
        assertTrue(await(provider().canAccess("actor1", "case:abc", AclAction.READ)));
    }

    @Test
    void canAccess_groupGrant_actorNotInGroup_returnsFalse() {
        await(provider().grant("group:managers", "case:abc", AclAction.READ, null));
        assertFalse(await(provider().canAccess("actor-no-groups", "case:abc", AclAction.READ)));
    }

    @Test
    void registerParent_childInheritsAccess() {
        await(provider().grant("actor1", "case:parent", AclAction.READ, null));
        await(provider().registerParent("planitem:child1", "case:parent"));
        assertTrue(await(provider().canAccess("actor1", "planitem:child1", AclAction.READ)));
    }

    @Test
    void registerParent_grandchildInheritsAccess() {
        await(provider().grant("actor1", "case:root", AclAction.READ, null));
        await(provider().registerParent("planitem:child", "case:root"));
        await(provider().registerParent("workitem:grandchild", "planitem:child"));
        assertTrue(await(provider().canAccess("actor1", "workitem:grandchild", AclAction.READ)));
    }

    @Test
    void registerParent_noGrantOnParent_returnsFalse() {
        await(provider().registerParent("planitem:child", "case:parent"));
        assertFalse(await(provider().canAccess("actor1", "planitem:child", AclAction.READ)));
    }

    @Test
    void canAccess_expiredGrant_returnsFalse() {
        await(provider().grant("actor1", "case:abc", AclAction.READ,
                Instant.now().minus(1, ChronoUnit.HOURS)));
        assertFalse(await(provider().canAccess("actor1", "case:abc", AclAction.READ)));
    }

    @Test
    void canAccess_futureExpiry_returnsTrue() {
        await(provider().grant("actor1", "case:abc", AclAction.READ,
                Instant.now().plus(1, ChronoUnit.HOURS)));
        assertTrue(await(provider().canAccess("actor1", "case:abc", AclAction.READ)));
    }

    @Test
    void accessibleResources_returnsMatchingResources() {
        await(provider().grant("actor1", "case:abc", AclAction.READ, null));
        await(provider().grant("actor1", "case:def", AclAction.READ, null));
        await(provider().grant("actor1", "case:ghi", AclAction.WRITE, null));

        List<String> readable = await(provider().accessibleResources("actor1",
                AclResourceType.CASE, AclAction.READ));
        assertEquals(2, readable.size());
        assertTrue(readable.contains("case:abc"));
        assertTrue(readable.contains("case:def"));
    }

    @Test
    void accessibleResources_excludesExpired() {
        await(provider().grant("actor1", "case:abc", AclAction.READ, null));
        await(provider().grant("actor1", "case:expired", AclAction.READ,
                Instant.now().minus(1, ChronoUnit.HOURS)));

        List<String> readable = await(provider().accessibleResources("actor1",
                AclResourceType.CASE, AclAction.READ));
        assertEquals(1, readable.size());
        assertTrue(readable.contains("case:abc"));
    }

    @Test
    void accessibleResources_includesGroupGrants() {
        await(provider().grant("group:managers", "case:abc", AclAction.READ, null));

        List<String> readable = await(provider().accessibleResources("actor1",
                AclResourceType.CASE, AclAction.READ));
        assertTrue(readable.contains("case:abc"));
    }

    @Test
    void grant_duplicateIsIdempotent() {
        await(provider().grant("actor1", "case:abc", AclAction.READ, null));
        await(provider().grant("actor1", "case:abc", AclAction.READ, null));
        assertTrue(await(provider().canAccess("actor1", "case:abc", AclAction.READ)));
    }

    @Test
    void revoke_nonExistentIsNoOp() {
        assertDoesNotThrow(() -> await(provider().revoke("actor1", "case:abc", AclAction.READ)));
    }
}
