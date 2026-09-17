package io.casehub.platform.acl.spring.jpa;

import io.casehub.platform.acl.jpa.AclAuditLogEntity;
import io.casehub.platform.acl.jpa.AclEntryEntity;
import io.casehub.platform.acl.jpa.ResourceParentEntity;
import io.casehub.platform.api.acl.AclAction;
import io.casehub.platform.api.acl.AclPage;
import io.casehub.platform.api.acl.AclQuery;
import io.casehub.platform.api.acl.ResourceId;
import io.casehub.platform.api.identity.GroupMembershipProvider;
import io.casehub.platform.testing.spring.SpringFixedCurrentPrincipal;
import io.casehub.platform.testing.spring.SpringTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import({SpringAccessControlProviderTest.TestConfig.class, SpringTestConfig.class})
class SpringAccessControlProviderTest {

    private static final String ACTOR = "user-1";
    private static final ResourceId RESOURCE = ResourceId.parse("case:case-1");

    @Configuration
    @AutoConfigurationPackage
    @EnableJpaRepositories(basePackageClasses = AclEntryEntityRepository.class)
    @EntityScan(basePackageClasses = {AclEntryEntity.class, AclAuditLogEntity.class, ResourceParentEntity.class})
    static class TestConfig {
        @Bean
        GroupMembershipProvider groupMembershipProvider() {
            return (actorId, tenancyId) -> Set.of();
        }

        @Bean
        SpringAccessControlProvider springAccessControlProvider(
                AclEntryEntityRepository entryRepo,
                AclAuditLogEntityRepository auditRepo,
                ResourceParentEntityRepository parentRepo,
                GroupMembershipProvider groupMembership,
                SpringFixedCurrentPrincipal principal) {
            return new SpringAccessControlProvider(entryRepo, auditRepo, parentRepo, groupMembership, principal);
        }
    }

    @Autowired SpringAccessControlProvider provider;

    @BeforeEach
    void setup() {
        provider.grant(ACTOR, RESOURCE, AclAction.READ, null);
    }

    @Test
    void grantAndCanAccess() {
        assertTrue(provider.canAccess(ACTOR, RESOURCE, AclAction.READ));
    }

    @Test
    void canAccessDeniedByDefault() {
        assertFalse(provider.canAccess(ACTOR, ResourceId.parse("case:case-999"), AclAction.READ));
    }

    @Test
    void revokeRemovesAccess() {
        provider.revoke(ACTOR, RESOURCE, AclAction.READ);
        assertFalse(provider.canAccess(ACTOR, RESOURCE, AclAction.READ));
    }

    @Test
    void denyOverridesGrant() {
        provider.deny(ACTOR, RESOURCE, AclAction.READ, null);
        assertFalse(provider.canAccess(ACTOR, RESOURCE, AclAction.READ));
    }

    @Test
    void removeDenyRestoresAccess() {
        provider.deny(ACTOR, RESOURCE, AclAction.READ, null);
        provider.removeDeny(ACTOR, RESOURCE, AclAction.READ);
        assertTrue(provider.canAccess(ACTOR, RESOURCE, AclAction.READ));
    }

    @Test
    void adminSatisfiesRead() {
        provider.grant(ACTOR, RESOURCE, AclAction.ADMIN, null);
        assertTrue(provider.canAccess(ACTOR, RESOURCE, AclAction.READ));
    }

    @Test
    void denyReadBlocksWrite() {
        provider.grant(ACTOR, RESOURCE, AclAction.WRITE, null);
        provider.deny(ACTOR, RESOURCE, AclAction.READ, null);
        assertFalse(provider.canAccess(ACTOR, RESOURCE, AclAction.WRITE));
    }

    @Test
    void wildcardGrant() {
        ResourceId wildcard = new ResourceId("case", "*");
        provider.grant(ACTOR, wildcard, AclAction.READ, null);
        assertTrue(provider.canAccess(ACTOR, ResourceId.parse("case:case-new"), AclAction.READ));
    }

    @Test
    void parentChainInheritance() {
        ResourceId parent = ResourceId.parse("plan:plan-1");
        ResourceId child = ResourceId.parse("case:case-child");
        provider.grant(ACTOR, parent, AclAction.READ, null);
        provider.registerParent(child, parent);
        assertTrue(provider.canAccess(ACTOR, child, AclAction.READ));
    }

    @Test
    void revokeAll() {
        provider.grant(ACTOR, RESOURCE, AclAction.WRITE, null);
        provider.revokeAll(ACTOR, RESOURCE);
        assertFalse(provider.canAccess(ACTOR, RESOURCE, AclAction.READ));
        assertFalse(provider.canAccess(ACTOR, RESOURCE, AclAction.WRITE));
    }

    @Test
    void accessibleResources() {
        provider.grant(ACTOR, ResourceId.parse("case:case-2"), AclAction.READ, null);
        List<ResourceId> resources = provider.accessibleResources(ACTOR, "case", AclAction.READ);
        assertEquals(2, resources.size());
    }

    @Test
    void accessibleResourcesExcludesDenied() {
        provider.grant(ACTOR, ResourceId.parse("case:case-2"), AclAction.READ, null);
        provider.deny(ACTOR, RESOURCE, AclAction.READ, null);
        List<ResourceId> resources = provider.accessibleResources(ACTOR, "case", AclAction.READ);
        assertEquals(1, resources.size());
        assertEquals("case:case-2", resources.getFirst().toString());
    }

    @Test
    void paginatedAccessibleResources() {
        for (int i = 0; i < 5; i++) {
            provider.grant(ACTOR, ResourceId.parse("case:case-" + i), AclAction.READ, null);
        }
        AclPage page1 = provider.accessibleResources(new AclQuery(ACTOR, "case", AclAction.READ, null, 3));
        assertEquals(3, page1.resourceIds().size());
        assertNotNull(page1.nextCursor());

        AclPage page2 = provider.accessibleResources(new AclQuery(ACTOR, "case", AclAction.READ, page1.nextCursor(), 3));
        assertTrue(page2.resourceIds().size() <= 3);
    }
}
