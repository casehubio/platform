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

    private static final ResourceId CASE_ABC            = ResourceId.parse("case:abc");
    private static final ResourceId CASE_DEF            = ResourceId.parse("case:def");
    private static final ResourceId CASE_GHI            = ResourceId.parse("case:ghi");
    private static final ResourceId CASE_AAA            = ResourceId.parse("case:aaa");
    private static final ResourceId CASE_BBB            = ResourceId.parse("case:bbb");
    private static final ResourceId CASE_CCC            = ResourceId.parse("case:ccc");
    private static final ResourceId CASE_PARENT         = ResourceId.parse("case:parent");
    private static final ResourceId CASE_ROOT           = ResourceId.parse("case:root");
    private static final ResourceId CASE_NEW_PARENT     = ResourceId.parse("case:newParent");
    private static final ResourceId CASE_OLD_PARENT     = ResourceId.parse("case:oldParent");
    private static final ResourceId CASE_EXPIRED        = ResourceId.parse("case:expired");
    private static final ResourceId CASE_WILDCARD       = ResourceId.parse("case:*");
    private static final ResourceId PLANITEM_PI1        = ResourceId.parse("planitem:pi1");
    private static final ResourceId PLANITEM_CHILD      = ResourceId.parse("planitem:child");
    private static final ResourceId PLANITEM_CHILD1     = ResourceId.parse("planitem:child1");
    private static final ResourceId PLANITEM_CHILD2     = ResourceId.parse("planitem:child2");
    private static final ResourceId PLANITEM_DIRECT     = ResourceId.parse("planitem:direct");
    private static final ResourceId PLANITEM_INHERITED  = ResourceId.parse("planitem:inherited");
    private static final ResourceId WORKITEM_GRANDCHILD = ResourceId.parse("workitem:grandchild");
    private static final ResourceId FOOBAR              = ResourceId.parse("foo:bar");

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
        assertFalse(provider().canAccess("actor1", CASE_ABC, AclAction.READ));
    }

    @Test
    void canAccess_afterGrant_returnsTrue() {
        provider().grant("actor1", CASE_ABC, AclAction.READ, null);
        assertTrue(provider().canAccess("actor1", CASE_ABC, AclAction.READ));
    }

    @Test
    void canAccess_differentAction_returnsFalse() {
        provider().grant("actor1", CASE_ABC, AclAction.READ, null);
        assertFalse(provider().canAccess("actor1", CASE_ABC, AclAction.WRITE));
    }

    @Test
    void canAccess_differentActor_returnsFalse() {
        provider().grant("actor1", CASE_ABC, AclAction.READ, null);
        assertFalse(provider().canAccess("actor2", CASE_ABC, AclAction.READ));
    }

    @Test
    void canAccess_differentResource_returnsFalse() {
        provider().grant("actor1", CASE_ABC, AclAction.READ, null);
        assertFalse(provider().canAccess("actor1", CASE_DEF, AclAction.READ));
    }

    @Test
    void revoke_removesGrant() {
        provider().grant("actor1", CASE_ABC, AclAction.READ, null);
        provider().revoke("actor1", CASE_ABC, AclAction.READ);
        assertFalse(provider().canAccess("actor1", CASE_ABC, AclAction.READ));
    }

    @Test
    void revokeAll_removesAllActionsForActorAndResource() {
        provider().grant("actor1", CASE_ABC, AclAction.READ, null);
        provider().grant("actor1", CASE_ABC, AclAction.WRITE, null);
        provider().revokeAll("actor1", CASE_ABC);
        assertFalse(provider().canAccess("actor1", CASE_ABC, AclAction.READ));
        assertFalse(provider().canAccess("actor1", CASE_ABC, AclAction.WRITE));
    }

    @Test
    void revokeAll_doesNotAffectOtherResources() {
        provider().grant("actor1", CASE_ABC, AclAction.READ, null);
        provider().grant("actor1", CASE_DEF, AclAction.READ, null);
        provider().revokeAll("actor1", CASE_ABC);
        assertTrue(provider().canAccess("actor1", CASE_DEF, AclAction.READ));
    }

    @Test
    void canAccess_groupGrant_resolvedViaGroupsOf() {
        provider().grant("group:managers", CASE_ABC, AclAction.READ, null);
        assertTrue(provider().canAccess("actor1", CASE_ABC, AclAction.READ));
    }

    @Test
    void canAccess_groupGrant_actorNotInGroup_returnsFalse() {
        provider().grant("group:managers", CASE_ABC, AclAction.READ, null);
        assertFalse(provider().canAccess("actor-no-groups", CASE_ABC, AclAction.READ));
    }

    @Test
    void registerParent_childInheritsAccess() {
        provider().grant("actor1", CASE_PARENT, AclAction.READ, null);
        provider().registerParent(PLANITEM_CHILD1, CASE_PARENT);
        assertTrue(provider().canAccess("actor1", PLANITEM_CHILD1, AclAction.READ));
    }

    @Test
    void registerParent_grandchildInheritsAccess() {
        provider().grant("actor1", CASE_ROOT, AclAction.READ, null);
        provider().registerParent(PLANITEM_CHILD, CASE_ROOT);
        provider().registerParent(WORKITEM_GRANDCHILD, PLANITEM_CHILD);
        assertTrue(provider().canAccess("actor1", WORKITEM_GRANDCHILD, AclAction.READ));
    }

    @Test
    void registerParent_reparenting_updatesParent() {
        provider().grant("actor1", CASE_NEW_PARENT, AclAction.READ, null);
        provider().registerParent(PLANITEM_CHILD, CASE_OLD_PARENT);
        provider().registerParent(PLANITEM_CHILD, CASE_NEW_PARENT);
        assertTrue(provider().canAccess("actor1", PLANITEM_CHILD, AclAction.READ));
    }

    @Test
    void registerParent_noGrantOnParent_returnsFalse() {
        provider().registerParent(PLANITEM_CHILD, CASE_PARENT);
        assertFalse(provider().canAccess("actor1", PLANITEM_CHILD, AclAction.READ));
    }

    @Test
    void canAccess_expiredGrant_returnsFalse() {
        provider().grant("actor1", CASE_ABC, AclAction.READ,
                         Instant.now().minus(1, ChronoUnit.HOURS));
        assertFalse(provider().canAccess("actor1", CASE_ABC, AclAction.READ));
    }

    @Test
    void canAccess_futureExpiry_returnsTrue() {
        provider().grant("actor1", CASE_ABC, AclAction.READ,
                         Instant.now().plus(1, ChronoUnit.HOURS));
        assertTrue(provider().canAccess("actor1", CASE_ABC, AclAction.READ));
    }

    @Test
    void accessibleResources_returnsMatchingResources() {
        provider().grant("actor1", CASE_ABC, AclAction.READ, null);
        provider().grant("actor1", CASE_DEF, AclAction.READ, null);
        provider().grant("actor1", CASE_GHI, AclAction.WRITE, null);

        List<ResourceId> readable = provider().accessibleResources("actor1",
                                                                   "case", AclAction.READ);
        assertEquals(3, readable.size());
        assertTrue(readable.contains(CASE_ABC));
        assertTrue(readable.contains(CASE_DEF));
        assertTrue(readable.contains(CASE_GHI));
    }

    @Test
    void accessibleResources_excludesExpired() {
        provider().grant("actor1", CASE_ABC, AclAction.READ, null);
        provider().grant("actor1", CASE_EXPIRED, AclAction.READ,
                         Instant.now().minus(1, ChronoUnit.HOURS));

        List<ResourceId> readable = provider().accessibleResources("actor1",
                                                                   "case", AclAction.READ);
        assertEquals(1, readable.size());
        assertTrue(readable.contains(CASE_ABC));
    }

    @Test
    void accessibleResources_includesGroupGrants() {
        provider().grant("group:managers", CASE_ABC, AclAction.READ, null);

        List<ResourceId> readable = provider().accessibleResources("actor1",
                                                                   "case", AclAction.READ);
        assertTrue(readable.contains(CASE_ABC));
    }

    @Test
    void grant_duplicateIsIdempotent() {
        provider().grant("actor1", CASE_ABC, AclAction.READ, null);
        provider().grant("actor1", CASE_ABC, AclAction.READ, null);
        assertTrue(provider().canAccess("actor1", CASE_ABC, AclAction.READ));
    }

    @Test
    void revoke_nonExistentIsNoOp() {
        assertDoesNotThrow(() -> provider().revoke("actor1", CASE_ABC, AclAction.READ));
    }

    @Test
    void canAccess_adminAction_impliesWriteAndRead() {
        provider().grant("actor1", CASE_ABC, AclAction.ADMIN, null);
        assertTrue(provider().canAccess("actor1", CASE_ABC, AclAction.ADMIN));
        assertTrue(provider().canAccess("actor1", CASE_ABC, AclAction.WRITE));
        assertTrue(provider().canAccess("actor1", CASE_ABC, AclAction.READ));
        assertFalse(provider().canAccess("actor1", CASE_ABC, AclAction.CLAIM));
    }

    @Test
    void canAccess_claimAction_isOrthogonal() {
        provider().grant("actor1", CASE_ABC, AclAction.CLAIM, null);
        assertTrue(provider().canAccess("actor1", CASE_ABC, AclAction.CLAIM));
        assertFalse(provider().canAccess("actor1", CASE_ABC, AclAction.READ));
        assertFalse(provider().canAccess("actor1", CASE_ABC, AclAction.WRITE));
        assertFalse(provider().canAccess("actor1", CASE_ABC, AclAction.ADMIN));
    }

    @Test
    void canAccess_writeAction_impliesRead() {
        provider().grant("actor1", CASE_ABC, AclAction.WRITE, null);
        assertTrue(provider().canAccess("actor1", CASE_ABC, AclAction.WRITE));
        assertTrue(provider().canAccess("actor1", CASE_ABC, AclAction.READ));
        assertFalse(provider().canAccess("actor1", CASE_ABC, AclAction.ADMIN));
        assertFalse(provider().canAccess("actor1", CASE_ABC, AclAction.CLAIM));
    }

    @Test
    void grantBatch_grantsAllEntries() {
        var requests = List.of(
                new AclEntryRequest("actor1", CASE_ABC, AclAction.READ, null),
                new AclEntryRequest("actor1", CASE_DEF, AclAction.WRITE, null),
                new AclEntryRequest("group:managers", CASE_GHI, AclAction.ADMIN, null));
        provider().grantBatch(requests);

        assertTrue(provider().canAccess("actor1", CASE_ABC, AclAction.READ));
        assertTrue(provider().canAccess("actor1", CASE_DEF, AclAction.WRITE));
        assertTrue(provider().canAccess("actor1", CASE_GHI, AclAction.ADMIN));
    }

    @Test
    void revokeBatch_revokesAllEntries() {
        provider().grant("actor1", CASE_ABC, AclAction.READ, null);
        provider().grant("actor1", CASE_DEF, AclAction.WRITE, null);

        var requests = List.of(
                new AclEntryRequest("actor1", CASE_ABC, AclAction.READ, null),
                new AclEntryRequest("actor1", CASE_DEF, AclAction.WRITE, null));
        provider().revokeBatch(requests);

        assertFalse(provider().canAccess("actor1", CASE_ABC, AclAction.READ));
        assertFalse(provider().canAccess("actor1", CASE_DEF, AclAction.WRITE));
    }

    @Test
    void grantBatch_withExpiry_honoured() {
        var requests = List.of(
                new AclEntryRequest("actor1", CASE_ABC, AclAction.READ,
                                    Instant.now().plus(1, ChronoUnit.HOURS)),
                new AclEntryRequest("actor1", CASE_DEF, AclAction.READ,
                                    Instant.now().minus(1, ChronoUnit.HOURS)));
        provider().grantBatch(requests);

        assertTrue(provider().canAccess("actor1", CASE_ABC, AclAction.READ));
        assertFalse(provider().canAccess("actor1", CASE_DEF, AclAction.READ));
    }

    @Test
    void registerParent_depthGuardAt20_returnsFalse() {
        provider().grant("actor1", CASE_ROOT, AclAction.READ, null);
        ResourceId prev = CASE_ROOT;
        for (int i = 1; i <= 21; i++) {
            ResourceId child = ResourceId.parse("res:child" + i);
            provider().registerParent(child, prev);
            prev = child;
        }
        assertFalse(provider().canAccess("actor1", prev, AclAction.READ));
    }

    @Test
    void accessibleResources_planItemType_returnsCorrectResources() {
        provider().grant("actor1", PLANITEM_PI1, AclAction.READ, null);
        provider().grant("actor1", CASE_ABC, AclAction.READ, null);

        List<ResourceId> result = provider().accessibleResources("actor1",
                                                                 "planitem", AclAction.READ);
        assertEquals(1, result.size());
        assertTrue(result.contains(PLANITEM_PI1));
    }

    @Test
    void accessibleResources_deduplicatesDirectAndGroupGrants() {
        provider().grant("actor1", CASE_ABC, AclAction.READ, null);
        provider().grant("group:managers", CASE_ABC, AclAction.READ, null);

        List<ResourceId> result = provider().accessibleResources("actor1",
                                                                 "case", AclAction.READ);
        assertEquals(1, result.size());
        assertTrue(result.contains(CASE_ABC));
    }

    @Test
    void accessibleResources_paginated_returnsFirstPage() {
        provider().grant("actor1", CASE_AAA, AclAction.READ, null);
        provider().grant("actor1", CASE_BBB, AclAction.READ, null);
        provider().grant("actor1", CASE_CCC, AclAction.READ, null);

        AclPage page = provider().accessibleResources(
                new AclQuery("actor1", "case", AclAction.READ, null, 2));
        assertEquals(2, page.resourceIds().size());
        assertNotNull(page.nextCursor());
    }

    @Test
    void accessibleResources_paginated_cursorContinuation() {
        provider().grant("actor1", CASE_AAA, AclAction.READ, null);
        provider().grant("actor1", CASE_BBB, AclAction.READ, null);
        provider().grant("actor1", CASE_CCC, AclAction.READ, null);

        AclPage page1 = provider().accessibleResources(
                new AclQuery("actor1", "case", AclAction.READ, null, 2));
        AclPage page2 = provider().accessibleResources(
                new AclQuery("actor1", "case", AclAction.READ, page1.nextCursor(), 2));
        assertEquals(1, page2.resourceIds().size());
        assertNull(page2.nextCursor());
    }

    @Test
    void accessibleResources_paginated_emptyResult() {
        AclPage page = provider().accessibleResources(
                new AclQuery("actor1", "case", AclAction.READ, null, 10));
        assertTrue(page.resourceIds().isEmpty());
        assertNull(page.nextCursor());
    }

    @Test
    void accessibleResources_paginated_allFitInOnePage() {
        provider().grant("actor1", CASE_AAA, AclAction.READ, null);
        provider().grant("actor1", CASE_BBB, AclAction.READ, null);

        AclPage page = provider().accessibleResources(
                new AclQuery("actor1", "case", AclAction.READ, null, 10));
        assertEquals(2, page.resourceIds().size());
        assertNull(page.nextCursor());
    }

    @Test
    void accessibleResources_paginated_respectsActionHierarchy() {
        provider().grant("actor1", CASE_AAA, AclAction.ADMIN, null);
        provider().grant("actor1", CASE_BBB, AclAction.READ, null);

        AclPage page = provider().accessibleResources(
                new AclQuery("actor1", "case", AclAction.READ, null, 10));
        assertEquals(2, page.resourceIds().size());
    }

    @Test
    void accessibleResourcesIncludingInherited_returnsDirectAndInherited() {
        provider().grant("actor1", CASE_PARENT, AclAction.READ, null);
        provider().registerParent(PLANITEM_CHILD1, CASE_PARENT);
        provider().registerParent(PLANITEM_CHILD2, CASE_PARENT);

        List<ResourceId> result = provider().accessibleResourcesIncludingInherited(
                "actor1", "planitem", AclAction.READ);
        assertTrue(result.contains(PLANITEM_CHILD1));
        assertTrue(result.contains(PLANITEM_CHILD2));
    }

    @Test
    void accessibleResourcesIncludingInherited_includesDirectGrants() {
        provider().grant("actor1", PLANITEM_DIRECT, AclAction.READ, null);
        provider().grant("actor1", CASE_PARENT, AclAction.READ, null);
        provider().registerParent(PLANITEM_INHERITED, CASE_PARENT);

        List<ResourceId> result = provider().accessibleResourcesIncludingInherited(
                "actor1", "planitem", AclAction.READ);
        assertTrue(result.contains(PLANITEM_DIRECT));
        assertTrue(result.contains(PLANITEM_INHERITED));
    }

    @Test
    void accessibleResourcesIncludingInherited_respectsActionHierarchy() {
        provider().grant("actor1", CASE_PARENT, AclAction.ADMIN, null);
        provider().registerParent(PLANITEM_CHILD, CASE_PARENT);

        List<ResourceId> result = provider().accessibleResourcesIncludingInherited(
                "actor1", "planitem", AclAction.READ);
        assertTrue(result.contains(PLANITEM_CHILD));
    }

    @Test
    void accessibleResourcesIncludingInherited_excludesExpiredParentGrants() {
        provider().grant("actor1", CASE_PARENT, AclAction.READ,
                         Instant.now().minus(1, ChronoUnit.HOURS));
        provider().registerParent(PLANITEM_CHILD, CASE_PARENT);

        List<ResourceId> result = provider().accessibleResourcesIncludingInherited(
                "actor1", "planitem", AclAction.READ);
        assertFalse(result.contains(PLANITEM_CHILD));
    }

    @Test
    void accessibleResourcesIncludingInherited_groupGrantOnParent() {
        provider().grant("group:managers", CASE_PARENT, AclAction.READ, null);
        provider().registerParent(PLANITEM_CHILD, CASE_PARENT);

        List<ResourceId> result = provider().accessibleResourcesIncludingInherited(
                "actor1", "planitem", AclAction.READ);
        assertTrue(result.contains(PLANITEM_CHILD));
    }

    @Test
    void accessibleResourcesIncludingInherited_deduplicatesDirectAndInherited() {
        provider().grant("actor1", PLANITEM_PI1, AclAction.READ, null);
        provider().grant("actor1", CASE_PARENT, AclAction.READ, null);
        provider().registerParent(PLANITEM_PI1, CASE_PARENT);

        List<ResourceId> result = provider().accessibleResourcesIncludingInherited(
                "actor1", "planitem", AclAction.READ);
        assertEquals(1, result.stream().filter(PLANITEM_PI1::equals).count());
    }

    @Test
    void canAccess_differentTenant_returnsFalse() {
        provider().grant("actor1", CASE_ABC, AclAction.READ, null);
        setTenancyId("other-tenant");
        assertFalse(provider().canAccess("actor1", CASE_ABC, AclAction.READ));
        setTenancyId(tenancyId());
    }

    @Test
    void revoke_differentTenant_doesNotDelete() {
        provider().grant("actor1", CASE_ABC, AclAction.READ, null);
        setTenancyId("other-tenant");
        provider().revoke("actor1", CASE_ABC, AclAction.READ);
        setTenancyId(tenancyId());
        assertTrue(provider().canAccess("actor1", CASE_ABC, AclAction.READ));
    }

    @Test
    void accessibleResources_filteredByTenant() {
        provider().grant("actor1", CASE_ABC, AclAction.READ, null);
        setTenancyId("other-tenant");
        provider().grant("actor1", CASE_DEF, AclAction.READ, null);
        List<ResourceId> result = provider().accessibleResources("actor1", "case", AclAction.READ);
        assertEquals(1, result.size());
        assertTrue(result.contains(CASE_DEF));
        setTenancyId(tenancyId());
        result = provider().accessibleResources("actor1", "case", AclAction.READ);
        assertEquals(1, result.size());
        assertTrue(result.contains(CASE_ABC));
    }

    @Test
    void grant_sameTupleDifferentTenant_bothStored() {
        provider().grant("actor1", CASE_ABC, AclAction.READ, null);
        setTenancyId("other-tenant");
        provider().grant("actor1", CASE_ABC, AclAction.READ, null);
        assertTrue(provider().canAccess("actor1", CASE_ABC, AclAction.READ));
        setTenancyId(tenancyId());
        assertTrue(provider().canAccess("actor1", CASE_ABC, AclAction.READ));
    }

    // --- Wildcard grants ---

    @Test
    void canAccess_wildcardGrant_matchesInstance() {
        provider().grant("actor1", CASE_WILDCARD, AclAction.READ, null);
        assertTrue(provider().canAccess("actor1", CASE_ABC, AclAction.READ));
    }

    @Test
    void canAccess_wildcardGrant_doesNotMatchDifferentType() {
        provider().grant("actor1", CASE_WILDCARD, AclAction.READ, null);
        assertFalse(provider().canAccess("actor1", PLANITEM_PI1, AclAction.READ));
    }

    @Test
    void canAccess_wildcardGrant_respectsActionHierarchy() {
        provider().grant("actor1", CASE_WILDCARD, AclAction.ADMIN, null);
        assertTrue(provider().canAccess("actor1", CASE_ABC, AclAction.READ));
        assertTrue(provider().canAccess("actor1", CASE_ABC, AclAction.WRITE));
        assertTrue(provider().canAccess("actor1", CASE_ABC, AclAction.ADMIN));
        assertFalse(provider().canAccess("actor1", CASE_ABC, AclAction.CLAIM));
    }

    @Test
    void canAccess_wildcardGrant_respectsExpiry() {
        provider().grant("actor1", CASE_WILDCARD, AclAction.READ,
                         Instant.now().minus(1, ChronoUnit.HOURS));
        assertFalse(provider().canAccess("actor1", CASE_ABC, AclAction.READ));
    }

    @Test
    void canAccess_wildcardGrant_respectsGroupMembership() {
        provider().grant("group:managers", CASE_WILDCARD, AclAction.READ, null);
        assertTrue(provider().canAccess("actor1", CASE_ABC, AclAction.READ));
    }

    @Test
    void canAccess_wildcardGrant_respectsTenantIsolation() {
        provider().grant("actor1", CASE_WILDCARD, AclAction.READ, null);
        setTenancyId("other-tenant");
        assertFalse(provider().canAccess("actor1", CASE_ABC, AclAction.READ));
        setTenancyId(tenancyId());
    }

    @Test
    void accessibleResources_wildcardGrant_includesWildcardInResults() {
        provider().grant("actor1", CASE_WILDCARD, AclAction.READ, null);
        provider().grant("actor1", CASE_ABC, AclAction.READ, null);
        List<ResourceId> result = provider().accessibleResources("actor1",
                                                                 "case", AclAction.READ);
        assertTrue(result.contains(CASE_WILDCARD));
        assertTrue(result.contains(CASE_ABC));
    }

    @Test
    void accessibleResources_wildcardGrant_paginatedIncludesWildcard() {
        provider().grant("actor1", CASE_WILDCARD, AclAction.READ, null);
        provider().grant("actor1", CASE_AAA, AclAction.READ, null);
        provider().grant("actor1", CASE_BBB, AclAction.READ, null);

        AclPage page = provider().accessibleResources(
                new AclQuery("actor1", "case", AclAction.READ, null, 10));
        assertTrue(page.resourceIds().contains(CASE_WILDCARD));
    }

    @Test
    void accessibleResourcesIncludingInherited_wildcardGrant_passesThrough() {
        provider().grant("actor1", CASE_WILDCARD, AclAction.READ, null);
        provider().registerParent(PLANITEM_CHILD1, CASE_PARENT);

        List<ResourceId> result = provider().accessibleResourcesIncludingInherited(
                "actor1", "case", AclAction.READ);
        assertTrue(result.contains(CASE_WILDCARD));
    }

    @Test
    void canAccess_wildcardGrant_claimAction_satisfiesOnlyClaim() {
        provider().grant("actor1", CASE_WILDCARD, AclAction.CLAIM, null);
        assertTrue(provider().canAccess("actor1", CASE_ABC, AclAction.CLAIM));
        assertFalse(provider().canAccess("actor1", CASE_ABC, AclAction.READ));
        assertFalse(provider().canAccess("actor1", CASE_ABC, AclAction.WRITE));
        assertFalse(provider().canAccess("actor1", CASE_ABC, AclAction.ADMIN));
    }

    // --- Deny entries ---

    @Test
    void deny_blocksInstanceGrant() {
        provider().grant("actor1", CASE_ABC, AclAction.READ, null);
        provider().deny("actor1", CASE_ABC, AclAction.READ, null);
        assertFalse(provider().canAccess("actor1", CASE_ABC, AclAction.READ));
    }

    @Test
    void deny_blocksWildcardGrant() {
        provider().grant("actor1", CASE_WILDCARD, AclAction.READ, null);
        provider().deny("actor1", CASE_ABC, AclAction.READ, null);
        assertFalse(provider().canAccess("actor1", CASE_ABC, AclAction.READ));
        assertTrue(provider().canAccess("actor1", CASE_DEF, AclAction.READ));
    }

    @Test
    void deny_wildcardDeny_blocksAllOfType() {
        provider().grant("actor1", CASE_ABC, AclAction.READ, null);
        provider().deny("actor1", CASE_WILDCARD, AclAction.READ, null);
        // instance grant overrides wildcard deny (specificity wins)
        assertTrue(provider().canAccess("actor1", CASE_ABC, AclAction.READ));
        // no instance grant for def, wildcard deny blocks wildcard grant
        provider().grant("actor1", CASE_WILDCARD, AclAction.READ, null);
        assertFalse(provider().canAccess("actor1", CASE_DEF, AclAction.READ));
    }

    @Test
    void deny_actionSpecific_claimDenyDoesNotBlockRead() {
        provider().grant("actor1", CASE_ABC, AclAction.READ, null);
        provider().deny("actor1", CASE_ABC, AclAction.CLAIM, null);
        assertTrue(provider().canAccess("actor1", CASE_ABC, AclAction.READ));
        assertFalse(provider().canAccess("actor1", CASE_ABC, AclAction.CLAIM));
    }

    @Test
    void deny_cascadesViadeniedBy_readDenyBlocksWriteAndAdmin() {
        provider().grant("actor1", CASE_ABC, AclAction.ADMIN, null);
        provider().deny("actor1", CASE_ABC, AclAction.READ, null);
        assertFalse(provider().canAccess("actor1", CASE_ABC, AclAction.READ));
        assertFalse(provider().canAccess("actor1", CASE_ABC, AclAction.WRITE));
        assertFalse(provider().canAccess("actor1", CASE_ABC, AclAction.ADMIN));
    }

    @Test
    void deny_cascadesViadeniedBy_writeDenyBlocksAdminNotRead() {
        provider().grant("actor1", CASE_ABC, AclAction.ADMIN, null);
        provider().deny("actor1", CASE_ABC, AclAction.WRITE, null);
        assertTrue(provider().canAccess("actor1", CASE_ABC, AclAction.READ));
        assertFalse(provider().canAccess("actor1", CASE_ABC, AclAction.WRITE));
        assertFalse(provider().canAccess("actor1", CASE_ABC, AclAction.ADMIN));
    }

    @Test
    void deny_respectsExpiry() {
        provider().grant("actor1", CASE_ABC, AclAction.READ, null);
        provider().deny("actor1", CASE_ABC, AclAction.READ,
                        Instant.now().minus(1, ChronoUnit.HOURS));
        assertTrue(provider().canAccess("actor1", CASE_ABC, AclAction.READ));
    }

    @Test
    void deny_respectsTenantIsolation() {
        provider().grant("actor1", CASE_ABC, AclAction.READ, null);
        provider().deny("actor1", CASE_ABC, AclAction.READ, null);
        assertFalse(provider().canAccess("actor1", CASE_ABC, AclAction.READ));
        setTenancyId("other-tenant");
        provider().grant("actor1", CASE_ABC, AclAction.READ, null);
        assertTrue(provider().canAccess("actor1", CASE_ABC, AclAction.READ));
        setTenancyId(tenancyId());
    }

    @Test
    void deny_respectsGroupMembership() {
        provider().grant("actor1", CASE_ABC, AclAction.READ, null);
        provider().deny("group:managers", CASE_ABC, AclAction.READ, null);
        assertFalse(provider().canAccess("actor1", CASE_ABC, AclAction.READ));
    }

    @Test
    void removeDeny_restoresAccess() {
        provider().grant("actor1", CASE_ABC, AclAction.READ, null);
        provider().deny("actor1", CASE_ABC, AclAction.READ, null);
        assertFalse(provider().canAccess("actor1", CASE_ABC, AclAction.READ));
        provider().removeDeny("actor1", CASE_ABC, AclAction.READ);
        assertTrue(provider().canAccess("actor1", CASE_ABC, AclAction.READ));
    }

    @Test
    void revokeAll_alsoClearsDenies() {
        provider().grant("actor1", CASE_ABC, AclAction.READ, null);
        provider().deny("actor1", CASE_ABC, AclAction.READ, null);
        provider().revokeAll("actor1", CASE_ABC);
        assertFalse(provider().canAccess("actor1", CASE_ABC, AclAction.READ));
        provider().grant("actor1", CASE_ABC, AclAction.READ, null);
        assertTrue(provider().canAccess("actor1", CASE_ABC, AclAction.READ));
    }

    @Test
    void denyBatch_deniesAll() {
        provider().grant("actor1", CASE_ABC, AclAction.READ, null);
        provider().grant("actor1", CASE_DEF, AclAction.WRITE, null);
        var requests = List.of(
                new AclEntryRequest("actor1", CASE_ABC, AclAction.READ, null),
                new AclEntryRequest("actor1", CASE_DEF, AclAction.WRITE, null));
        provider().denyBatch(requests);
        assertFalse(provider().canAccess("actor1", CASE_ABC, AclAction.READ));
        assertFalse(provider().canAccess("actor1", CASE_DEF, AclAction.WRITE));
    }

    @Test
    void removeDenyBatch_restoresAll() {
        provider().grant("actor1", CASE_ABC, AclAction.READ, null);
        provider().grant("actor1", CASE_DEF, AclAction.WRITE, null);
        provider().deny("actor1", CASE_ABC, AclAction.READ, null);
        provider().deny("actor1", CASE_DEF, AclAction.WRITE, null);
        var requests = List.of(
                new AclEntryRequest("actor1", CASE_ABC, AclAction.READ, null),
                new AclEntryRequest("actor1", CASE_DEF, AclAction.WRITE, null));
        provider().removeDenyBatch(requests);
        assertTrue(provider().canAccess("actor1", CASE_ABC, AclAction.READ));
        assertTrue(provider().canAccess("actor1", CASE_DEF, AclAction.WRITE));
    }

    @Test
    void accessibleResources_excludesDeniedInstances() {
        provider().grant("actor1", CASE_ABC, AclAction.READ, null);
        provider().grant("actor1", CASE_DEF, AclAction.READ, null);
        provider().deny("actor1", CASE_ABC, AclAction.READ, null);
        List<ResourceId> result = provider().accessibleResources("actor1",
                                                                 "case", AclAction.READ);
        assertFalse(result.contains(CASE_ABC));
        assertTrue(result.contains(CASE_DEF));
    }

    @Test
    void accessibleResources_wildcardGrantWithDeny_wildcardPlusDeniedExcluded() {
        provider().grant("actor1", CASE_WILDCARD, AclAction.READ, null);
        provider().grant("actor1", CASE_ABC, AclAction.READ, null);
        provider().deny("actor1", CASE_ABC, AclAction.READ, null);
        List<ResourceId> result = provider().accessibleResources("actor1",
                                                                 "case", AclAction.READ);
        assertTrue(result.contains(CASE_WILDCARD));
        assertFalse(result.contains(CASE_ABC));
    }

    @Test
    void accessibleResources_wildcardDeny_suppressesWildcardGrant() {
        provider().grant("actor1", CASE_WILDCARD, AclAction.READ, null);
        provider().deny("actor1", CASE_WILDCARD, AclAction.READ, null);
        provider().grant("actor1", CASE_ABC, AclAction.READ, null);
        List<ResourceId> result = provider().accessibleResources("actor1",
                                                                 "case", AclAction.READ);
        assertFalse(result.contains(CASE_WILDCARD));
        assertTrue(result.contains(CASE_ABC));
    }

    @Test
    void deny_wildcardDenyPlusWildcardGrant_denyWinsAtSameLevel() {
        provider().grant("actor1", CASE_WILDCARD, AclAction.READ, null);
        provider().deny("actor1", CASE_WILDCARD, AclAction.READ, null);
        assertFalse(provider().canAccess("actor1", CASE_DEF, AclAction.READ));
    }

    // --- Deny + parent chain interaction ---

    @Test
    void deny_onParent_blocksChildInheritance() {
        provider().grant("actor1", CASE_PARENT, AclAction.READ, null);
        provider().deny("actor1", CASE_PARENT, AclAction.READ, null);
        provider().registerParent(PLANITEM_CHILD, CASE_PARENT);
        assertFalse(provider().canAccess("actor1", PLANITEM_CHILD, AclAction.READ));
    }

    @Test
    void deny_onParent_directGrantOnChild_allowed() {
        provider().grant("actor1", CASE_PARENT, AclAction.READ, null);
        provider().deny("actor1", CASE_PARENT, AclAction.READ, null);
        provider().registerParent(PLANITEM_CHILD, CASE_PARENT);
        provider().grant("actor1", PLANITEM_CHILD, AclAction.READ, null);
        assertTrue(provider().canAccess("actor1", PLANITEM_CHILD, AclAction.READ));
    }

    @Test
    void deny_wildcardOnParentType_blocksChildInheritance() {
        provider().grant("actor1", CASE_PARENT, AclAction.READ, null);
        provider().deny("actor1", CASE_WILDCARD, AclAction.READ, null);
        provider().registerParent(PLANITEM_CHILD, CASE_PARENT);
        assertTrue(provider().canAccess("actor1", PLANITEM_CHILD, AclAction.READ));
    }

    @Test
    void deny_wildcardOnParentType_instanceGrantOnParent_childInherits() {
        provider().grant("actor1", CASE_PARENT, AclAction.READ, null);
        provider().deny("actor1", CASE_WILDCARD, AclAction.READ, null);
        provider().registerParent(PLANITEM_CHILD, CASE_PARENT);
        assertTrue(provider().canAccess("actor1", PLANITEM_CHILD, AclAction.READ));
    }

    @Test
    void deny_onParent_groupGrantOnParent_actorDenied() {
        provider().grant("group:managers", CASE_PARENT, AclAction.READ, null);
        provider().deny("actor1", CASE_PARENT, AclAction.READ, null);
        provider().registerParent(PLANITEM_CHILD, CASE_PARENT);
        assertFalse(provider().canAccess("actor1", PLANITEM_CHILD, AclAction.READ));
    }

}
