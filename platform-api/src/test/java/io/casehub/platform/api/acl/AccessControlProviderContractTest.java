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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    void grantBatch_grantsAllEntries() {
        var requests = List.of(
                new AclEntryRequest("actor1", "case:abc", AclAction.READ, null),
                new AclEntryRequest("actor1", "case:def", AclAction.WRITE, null),
                new AclEntryRequest("group:managers", "case:ghi", AclAction.ADMIN, null));
        provider().grantBatch(requests);

        assertTrue(provider().canAccess("actor1", "case:abc", AclAction.READ));
        assertTrue(provider().canAccess("actor1", "case:def", AclAction.WRITE));
        assertTrue(provider().canAccess("actor1", "case:ghi", AclAction.ADMIN));
    }

    @Test
    void revokeBatch_revokesAllEntries() {
        provider().grant("actor1", "case:abc", AclAction.READ, null);
        provider().grant("actor1", "case:def", AclAction.WRITE, null);

        var requests = List.of(
                new AclEntryRequest("actor1", "case:abc", AclAction.READ, null),
                new AclEntryRequest("actor1", "case:def", AclAction.WRITE, null));
        provider().revokeBatch(requests);

        assertFalse(provider().canAccess("actor1", "case:abc", AclAction.READ));
        assertFalse(provider().canAccess("actor1", "case:def", AclAction.WRITE));
    }

    @Test
    void grantBatch_withExpiry_honoured() {
        var requests = List.of(
                new AclEntryRequest("actor1", "case:abc", AclAction.READ,
                                    Instant.now().plus(1, ChronoUnit.HOURS)),
                new AclEntryRequest("actor1", "case:def", AclAction.READ,
                                    Instant.now().minus(1, ChronoUnit.HOURS)));
        provider().grantBatch(requests);

        assertTrue(provider().canAccess("actor1", "case:abc", AclAction.READ));
        assertFalse(provider().canAccess("actor1", "case:def", AclAction.READ));
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
    void accessibleResources_paginated_returnsFirstPage() {
        provider().grant("actor1", "case:aaa", AclAction.READ, null);
        provider().grant("actor1", "case:bbb", AclAction.READ, null);
        provider().grant("actor1", "case:ccc", AclAction.READ, null);

        AclPage page = provider().accessibleResources(
                new AclQuery("actor1", AclResourceType.CASE, AclAction.READ, null, 2));
        assertEquals(2, page.resourceIds().size());
        assertNotNull(page.nextCursor());
    }

    @Test
    void accessibleResources_paginated_cursorContinuation() {
        provider().grant("actor1", "case:aaa", AclAction.READ, null);
        provider().grant("actor1", "case:bbb", AclAction.READ, null);
        provider().grant("actor1", "case:ccc", AclAction.READ, null);

        AclPage page1 = provider().accessibleResources(
                new AclQuery("actor1", AclResourceType.CASE, AclAction.READ, null, 2));
        AclPage page2 = provider().accessibleResources(
                new AclQuery("actor1", AclResourceType.CASE, AclAction.READ, page1.nextCursor(), 2));
        assertEquals(1, page2.resourceIds().size());
        assertNull(page2.nextCursor());
    }

    @Test
    void accessibleResources_paginated_emptyResult() {
        AclPage page = provider().accessibleResources(
                new AclQuery("actor1", AclResourceType.CASE, AclAction.READ, null, 10));
        assertTrue(page.resourceIds().isEmpty());
        assertNull(page.nextCursor());
    }

    @Test
    void accessibleResources_paginated_allFitInOnePage() {
        provider().grant("actor1", "case:aaa", AclAction.READ, null);
        provider().grant("actor1", "case:bbb", AclAction.READ, null);

        AclPage page = provider().accessibleResources(
                new AclQuery("actor1", AclResourceType.CASE, AclAction.READ, null, 10));
        assertEquals(2, page.resourceIds().size());
        assertNull(page.nextCursor());
    }

    @Test
    void accessibleResources_paginated_respectsActionHierarchy() {
        provider().grant("actor1", "case:aaa", AclAction.ADMIN, null);
        provider().grant("actor1", "case:bbb", AclAction.READ, null);

        AclPage page = provider().accessibleResources(
                new AclQuery("actor1", AclResourceType.CASE, AclAction.READ, null, 10));
        assertEquals(2, page.resourceIds().size());
    }

    @Test
    void accessibleResourcesIncludingInherited_returnsDirectAndInherited() {
        provider().grant("actor1", "case:parent", AclAction.READ, null);
        provider().registerParent("planitem:child1", "case:parent");
        provider().registerParent("planitem:child2", "case:parent");

        List<String> result = provider().accessibleResourcesIncludingInherited(
                "actor1", AclResourceType.PLAN_ITEM, AclAction.READ);
        assertTrue(result.contains("planitem:child1"));
        assertTrue(result.contains("planitem:child2"));
    }

    @Test
    void accessibleResourcesIncludingInherited_includesDirectGrants() {
        provider().grant("actor1", "planitem:direct", AclAction.READ, null);
        provider().grant("actor1", "case:parent", AclAction.READ, null);
        provider().registerParent("planitem:inherited", "case:parent");

        List<String> result = provider().accessibleResourcesIncludingInherited(
                "actor1", AclResourceType.PLAN_ITEM, AclAction.READ);
        assertTrue(result.contains("planitem:direct"));
        assertTrue(result.contains("planitem:inherited"));
    }

    @Test
    void accessibleResourcesIncludingInherited_respectsActionHierarchy() {
        provider().grant("actor1", "case:parent", AclAction.ADMIN, null);
        provider().registerParent("planitem:child", "case:parent");

        List<String> result = provider().accessibleResourcesIncludingInherited(
                "actor1", AclResourceType.PLAN_ITEM, AclAction.READ);
        assertTrue(result.contains("planitem:child"));
    }

    @Test
    void accessibleResourcesIncludingInherited_excludesExpiredParentGrants() {
        provider().grant("actor1", "case:parent", AclAction.READ,
                         Instant.now().minus(1, ChronoUnit.HOURS));
        provider().registerParent("planitem:child", "case:parent");

        List<String> result = provider().accessibleResourcesIncludingInherited(
                "actor1", AclResourceType.PLAN_ITEM, AclAction.READ);
        assertFalse(result.contains("planitem:child"));
    }

    @Test
    void accessibleResourcesIncludingInherited_groupGrantOnParent() {
        provider().grant("group:managers", "case:parent", AclAction.READ, null);
        provider().registerParent("planitem:child", "case:parent");

        List<String> result = provider().accessibleResourcesIncludingInherited(
                "actor1", AclResourceType.PLAN_ITEM, AclAction.READ);
        assertTrue(result.contains("planitem:child"));
    }

    @Test
    void accessibleResourcesIncludingInherited_deduplicatesDirectAndInherited() {
        provider().grant("actor1", "planitem:pi1", AclAction.READ, null);
        provider().grant("actor1", "case:parent", AclAction.READ, null);
        provider().registerParent("planitem:pi1", "case:parent");

        List<String> result = provider().accessibleResourcesIncludingInherited(
                "actor1", AclResourceType.PLAN_ITEM, AclAction.READ);
        assertEquals(1, result.stream().filter("planitem:pi1"::equals).count());
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
// --- Wildcard grants ---

    @Test
    void canAccess_wildcardGrant_matchesInstance() {
        provider().grant("actor1", "case:*", AclAction.READ, null);
        assertTrue(provider().canAccess("actor1", "case:abc", AclAction.READ));
    }

    @Test
    void canAccess_wildcardGrant_doesNotMatchDifferentType() {
        provider().grant("actor1", "case:*", AclAction.READ, null);
        assertFalse(provider().canAccess("actor1", "planitem:pi1", AclAction.READ));
    }

    @Test
    void canAccess_wildcardGrant_respectsActionHierarchy() {
        provider().grant("actor1", "case:*", AclAction.ADMIN, null);
        assertTrue(provider().canAccess("actor1", "case:abc", AclAction.READ));
        assertTrue(provider().canAccess("actor1", "case:abc", AclAction.WRITE));
        assertTrue(provider().canAccess("actor1", "case:abc", AclAction.ADMIN));
        assertFalse(provider().canAccess("actor1", "case:abc", AclAction.CLAIM));
    }

    @Test
    void canAccess_wildcardGrant_respectsExpiry() {
        provider().grant("actor1", "case:*", AclAction.READ,
                         Instant.now().minus(1, ChronoUnit.HOURS));
        assertFalse(provider().canAccess("actor1", "case:abc", AclAction.READ));
    }

    @Test
    void canAccess_wildcardGrant_respectsGroupMembership() {
        provider().grant("group:managers", "case:*", AclAction.READ, null);
        assertTrue(provider().canAccess("actor1", "case:abc", AclAction.READ));
    }

    @Test
    void canAccess_wildcardGrant_respectsTenantIsolation() {
        provider().grant("actor1", "case:*", AclAction.READ, null);
        setTenancyId("other-tenant");
        assertFalse(provider().canAccess("actor1", "case:abc", AclAction.READ));
        setTenancyId(tenancyId());
    }

    @Test
    void canAccess_noColonInResourceId_noWildcardCheck() {
        provider().grant("actor1", "foobar", AclAction.READ, null);
        assertTrue(provider().canAccess("actor1", "foobar", AclAction.READ));
        provider().grant("actor1", "*", AclAction.READ, null);
        assertFalse(provider().canAccess("actor1", "anything", AclAction.READ));
    }

    @Test
    void accessibleResources_wildcardGrant_includesWildcardInResults() {
        provider().grant("actor1", "case:*", AclAction.READ, null);
        provider().grant("actor1", "case:abc", AclAction.READ, null);
        List<String> result = provider().accessibleResources("actor1",
                                                             AclResourceType.CASE, AclAction.READ);
        assertTrue(result.contains("case:*"));
        assertTrue(result.contains("case:abc"));
    }

    @Test
    void accessibleResources_wildcardGrant_paginatedIncludesWildcard() {
        provider().grant("actor1", "case:*", AclAction.READ, null);
        provider().grant("actor1", "case:aaa", AclAction.READ, null);
        provider().grant("actor1", "case:bbb", AclAction.READ, null);

        AclPage page = provider().accessibleResources(
                new AclQuery("actor1", AclResourceType.CASE, AclAction.READ, null, 10));
        assertTrue(page.resourceIds().contains("case:*"));
    }

    @Test
    void accessibleResourcesIncludingInherited_wildcardGrant_passesThrough() {
        provider().grant("actor1", "case:*", AclAction.READ, null);
        provider().registerParent("planitem:child1", "case:parent");

        List<String> result = provider().accessibleResourcesIncludingInherited(
                "actor1", AclResourceType.CASE, AclAction.READ);
        assertTrue(result.contains("case:*"));
    }

    @Test
    void canAccess_wildcardGrant_claimAction_satisfiesOnlyClaim() {
        provider().grant("actor1", "case:*", AclAction.CLAIM, null);
        assertTrue(provider().canAccess("actor1", "case:abc", AclAction.CLAIM));
        assertFalse(provider().canAccess("actor1", "case:abc", AclAction.READ));
        assertFalse(provider().canAccess("actor1", "case:abc", AclAction.WRITE));
        assertFalse(provider().canAccess("actor1", "case:abc", AclAction.ADMIN));
    }
// --- Deny entries ---

    @Test
    void deny_blocksInstanceGrant() {
        provider().grant("actor1", "case:abc", AclAction.READ, null);
        provider().deny("actor1", "case:abc", AclAction.READ, null);
        assertFalse(provider().canAccess("actor1", "case:abc", AclAction.READ));
    }

    @Test
    void deny_blocksWildcardGrant() {
        provider().grant("actor1", "case:*", AclAction.READ, null);
        provider().deny("actor1", "case:abc", AclAction.READ, null);
        assertFalse(provider().canAccess("actor1", "case:abc", AclAction.READ));
        assertTrue(provider().canAccess("actor1", "case:def", AclAction.READ));
    }

    @Test
    void deny_wildcardDeny_blocksAllOfType() {
        provider().grant("actor1", "case:abc", AclAction.READ, null);
        provider().deny("actor1", "case:*", AclAction.READ, null);
        // instance grant overrides wildcard deny (specificity wins)
        assertTrue(provider().canAccess("actor1", "case:abc", AclAction.READ));
        // no instance grant for def, wildcard deny blocks wildcard grant
        provider().grant("actor1", "case:*", AclAction.READ, null);
        assertFalse(provider().canAccess("actor1", "case:def", AclAction.READ));
    }

    @Test
    void deny_actionSpecific_claimDenyDoesNotBlockRead() {
        provider().grant("actor1", "case:abc", AclAction.READ, null);
        provider().deny("actor1", "case:abc", AclAction.CLAIM, null);
        assertTrue(provider().canAccess("actor1", "case:abc", AclAction.READ));
        assertFalse(provider().canAccess("actor1", "case:abc", AclAction.CLAIM));
    }

    @Test
    void deny_cascadesViadeniedBy_readDenyBlocksWriteAndAdmin() {
        provider().grant("actor1", "case:abc", AclAction.ADMIN, null);
        provider().deny("actor1", "case:abc", AclAction.READ, null);
        assertFalse(provider().canAccess("actor1", "case:abc", AclAction.READ));
        assertFalse(provider().canAccess("actor1", "case:abc", AclAction.WRITE));
        assertFalse(provider().canAccess("actor1", "case:abc", AclAction.ADMIN));
    }

    @Test
    void deny_cascadesViadeniedBy_writeDenyBlocksAdminNotRead() {
        provider().grant("actor1", "case:abc", AclAction.ADMIN, null);
        provider().deny("actor1", "case:abc", AclAction.WRITE, null);
        assertTrue(provider().canAccess("actor1", "case:abc", AclAction.READ));
        assertFalse(provider().canAccess("actor1", "case:abc", AclAction.WRITE));
        assertFalse(provider().canAccess("actor1", "case:abc", AclAction.ADMIN));
    }

    @Test
    void deny_respectsExpiry() {
        provider().grant("actor1", "case:abc", AclAction.READ, null);
        provider().deny("actor1", "case:abc", AclAction.READ,
                        Instant.now().minus(1, ChronoUnit.HOURS));
        assertTrue(provider().canAccess("actor1", "case:abc", AclAction.READ));
    }

    @Test
    void deny_respectsTenantIsolation() {
        provider().grant("actor1", "case:abc", AclAction.READ, null);
        provider().deny("actor1", "case:abc", AclAction.READ, null);
        assertFalse(provider().canAccess("actor1", "case:abc", AclAction.READ));
        setTenancyId("other-tenant");
        provider().grant("actor1", "case:abc", AclAction.READ, null);
        assertTrue(provider().canAccess("actor1", "case:abc", AclAction.READ));
        setTenancyId(tenancyId());
    }

    @Test
    void deny_respectsGroupMembership() {
        provider().grant("actor1", "case:abc", AclAction.READ, null);
        provider().deny("group:managers", "case:abc", AclAction.READ, null);
        assertFalse(provider().canAccess("actor1", "case:abc", AclAction.READ));
    }

    @Test
    void removeDeny_restoresAccess() {
        provider().grant("actor1", "case:abc", AclAction.READ, null);
        provider().deny("actor1", "case:abc", AclAction.READ, null);
        assertFalse(provider().canAccess("actor1", "case:abc", AclAction.READ));
        provider().removeDeny("actor1", "case:abc", AclAction.READ);
        assertTrue(provider().canAccess("actor1", "case:abc", AclAction.READ));
    }

    @Test
    void revokeAll_alsoClearsDenies() {
        provider().grant("actor1", "case:abc", AclAction.READ, null);
        provider().deny("actor1", "case:abc", AclAction.READ, null);
        provider().revokeAll("actor1", "case:abc");
        // both grant and deny removed — default behavior (canAccess returns false for real impls)
        assertFalse(provider().canAccess("actor1", "case:abc", AclAction.READ));
        // re-grant should work (deny was cleared)
        provider().grant("actor1", "case:abc", AclAction.READ, null);
        assertTrue(provider().canAccess("actor1", "case:abc", AclAction.READ));
    }

    @Test
    void denyBatch_deniesAll() {
        provider().grant("actor1", "case:abc", AclAction.READ, null);
        provider().grant("actor1", "case:def", AclAction.WRITE, null);
        var requests = List.of(
                new AclEntryRequest("actor1", "case:abc", AclAction.READ, null),
                new AclEntryRequest("actor1", "case:def", AclAction.WRITE, null));
        provider().denyBatch(requests);
        assertFalse(provider().canAccess("actor1", "case:abc", AclAction.READ));
        assertFalse(provider().canAccess("actor1", "case:def", AclAction.WRITE));
    }

    @Test
    void removeDenyBatch_restoresAll() {
        provider().grant("actor1", "case:abc", AclAction.READ, null);
        provider().grant("actor1", "case:def", AclAction.WRITE, null);
        provider().deny("actor1", "case:abc", AclAction.READ, null);
        provider().deny("actor1", "case:def", AclAction.WRITE, null);
        var requests = List.of(
                new AclEntryRequest("actor1", "case:abc", AclAction.READ, null),
                new AclEntryRequest("actor1", "case:def", AclAction.WRITE, null));
        provider().removeDenyBatch(requests);
        assertTrue(provider().canAccess("actor1", "case:abc", AclAction.READ));
        assertTrue(provider().canAccess("actor1", "case:def", AclAction.WRITE));
    }

    @Test
    void accessibleResources_excludesDeniedInstances() {
        provider().grant("actor1", "case:abc", AclAction.READ, null);
        provider().grant("actor1", "case:def", AclAction.READ, null);
        provider().deny("actor1", "case:abc", AclAction.READ, null);
        List<String> result = provider().accessibleResources("actor1",
                                                             AclResourceType.CASE, AclAction.READ);
        assertFalse(result.contains("case:abc"));
        assertTrue(result.contains("case:def"));
    }

    @Test
    void accessibleResources_wildcardGrantWithDeny_wildcardPlusDeniedExcluded() {
        provider().grant("actor1", "case:*", AclAction.READ, null);
        provider().grant("actor1", "case:abc", AclAction.READ, null);
        provider().deny("actor1", "case:abc", AclAction.READ, null);
        List<String> result = provider().accessibleResources("actor1",
                                                             AclResourceType.CASE, AclAction.READ);
        assertTrue(result.contains("case:*"));
        assertFalse(result.contains("case:abc"));
    }

    @Test
    void accessibleResources_wildcardDeny_suppressesWildcardGrant() {
        provider().grant("actor1", "case:*", AclAction.READ, null);
        provider().deny("actor1", "case:*", AclAction.READ, null);
        provider().grant("actor1", "case:abc", AclAction.READ, null);
        List<String> result = provider().accessibleResources("actor1",
                                                             AclResourceType.CASE, AclAction.READ);
        assertFalse(result.contains("case:*"));
        assertTrue(result.contains("case:abc"));
    }

    @Test
    void deny_wildcardDenyPlusWildcardGrant_denyWinsAtSameLevel() {
        provider().grant("actor1", "case:*", AclAction.READ, null);
        provider().deny("actor1", "case:*", AclAction.READ, null);
        assertFalse(provider().canAccess("actor1", "case:def", AclAction.READ));
    }
// --- Deny + parent chain interaction ---

    @Test
    void deny_onParent_blocksChildInheritance() {
        provider().grant("actor1", "case:parent", AclAction.READ, null);
        provider().deny("actor1", "case:parent", AclAction.READ, null);
        provider().registerParent("planitem:child", "case:parent");
        assertFalse(provider().canAccess("actor1", "planitem:child", AclAction.READ));
    }

    @Test
    void deny_onParent_directGrantOnChild_allowed() {
        provider().grant("actor1", "case:parent", AclAction.READ, null);
        provider().deny("actor1", "case:parent", AclAction.READ, null);
        provider().registerParent("planitem:child", "case:parent");
        provider().grant("actor1", "planitem:child", AclAction.READ, null);
        assertTrue(provider().canAccess("actor1", "planitem:child", AclAction.READ));
    }

    @Test
    void deny_wildcardOnParentType_blocksChildInheritance() {
        provider().grant("actor1", "case:parent", AclAction.READ, null);
        provider().deny("actor1", "case:*", AclAction.READ, null);
        provider().registerParent("planitem:child", "case:parent");
        // instance grant on parent overrides wildcard deny on parent type (specificity)
        assertTrue(provider().canAccess("actor1", "planitem:child", AclAction.READ));
    }

    @Test
    void deny_wildcardOnParentType_instanceGrantOnParent_childInherits() {
        provider().grant("actor1", "case:parent", AclAction.READ, null);
        provider().deny("actor1", "case:*", AclAction.READ, null);
        provider().registerParent("planitem:child", "case:parent");
        // parent has instance grant (specificity > wildcard deny) → child inherits
        assertTrue(provider().canAccess("actor1", "planitem:child", AclAction.READ));
    }

    @Test
    void deny_onParent_groupGrantOnParent_actorDenied() {
        provider().grant("group:managers", "case:parent", AclAction.READ, null);
        provider().deny("actor1", "case:parent", AclAction.READ, null);
        provider().registerParent("planitem:child", "case:parent");
        // both instance-level on parent: deny wins over grant (same specificity, deny first)
        assertFalse(provider().canAccess("actor1", "planitem:child", AclAction.READ));
    }


}
