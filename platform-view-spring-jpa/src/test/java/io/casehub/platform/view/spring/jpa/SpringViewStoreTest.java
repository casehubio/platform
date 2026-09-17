package io.casehub.platform.view.spring.jpa;

import io.casehub.platform.api.path.Path;
import io.casehub.platform.api.view.SubjectViewSpec;
import io.casehub.platform.view.jpa.SubjectViewEntity;
import io.casehub.platform.view.jpa.ViewMembershipEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import(SpringViewStoreTest.TestConfig.class)
class SpringViewStoreTest {

    private static final String TENANT = "test-tenant";

    @Configuration
    @AutoConfigurationPackage
    @EnableJpaRepositories(basePackageClasses = SubjectViewEntityRepository.class)
    @EntityScan(basePackageClasses = {SubjectViewEntity.class, ViewMembershipEntity.class})
    static class TestConfig {
        @Bean
        SpringSubjectViewStore springSubjectViewStore(SubjectViewEntityRepository repo) {
            return new SpringSubjectViewStore(repo);
        }

        @Bean
        SpringCrossTenantSubjectViewStore springCrossTenantSubjectViewStore(SubjectViewEntityRepository repo) {
            return new SpringCrossTenantSubjectViewStore(repo);
        }

        @Bean
        SpringViewMembershipTracker springViewMembershipTracker(ViewMembershipEntityRepository repo) {
            return new SpringViewMembershipTracker(repo);
        }
    }

    @Autowired SpringSubjectViewStore viewStore;
    @Autowired SpringCrossTenantSubjectViewStore crossTenantStore;
    @Autowired SpringViewMembershipTracker tracker;

    private SubjectViewSpec testSpec(String name) {
        return new SubjectViewSpec(null, name, TENANT, "/cases/**",
                Path.root(), "createdAt", "DESC", null, null);
    }

    @Test
    void saveAndFindById() {
        SubjectViewSpec saved = viewStore.save(testSpec("My View"));
        assertNotNull(saved.id());

        var found = viewStore.findById(saved.id());
        assertTrue(found.isPresent());
        assertEquals("My View", found.get().name());
    }

    @Test
    void findByTenancy() {
        viewStore.save(testSpec("View A"));
        viewStore.save(testSpec("View B"));

        List<SubjectViewSpec> views = viewStore.findByTenancy(TENANT);
        assertEquals(2, views.size());
    }

    @Test
    void delete() {
        SubjectViewSpec saved = viewStore.save(testSpec("To Delete"));
        assertTrue(viewStore.delete(saved.id()));
        assertTrue(viewStore.findById(saved.id()).isEmpty());
    }

    @Test
    void deleteNonExistent() {
        assertFalse(viewStore.delete(UUID.randomUUID()));
    }

    @Test
    void crossTenantFindDistinctTenancyIds() {
        viewStore.save(testSpec("View 1"));
        viewStore.save(new SubjectViewSpec(null, "View 2", "other-tenant", "/items/**",
                null, null, null, null, null));

        List<String> tenancyIds = crossTenantStore.findDistinctTenancyIds();
        assertEquals(2, tenancyIds.size());
        assertTrue(tenancyIds.contains(TENANT));
        assertTrue(tenancyIds.contains("other-tenant"));
    }

    @Test
    void membershipUpdateAndGet() {
        UUID subjectId = UUID.randomUUID();
        UUID viewId1 = UUID.randomUUID();
        UUID viewId2 = UUID.randomUUID();

        tracker.updateMembership(subjectId, Map.of(viewId1, "View A", viewId2, "View B"));

        Map<UUID, String> membership = tracker.getLastKnownMembership(subjectId);
        assertEquals(2, membership.size());
        assertEquals("View A", membership.get(viewId1));
    }

    @Test
    void membershipBatchGet() {
        UUID sub1 = UUID.randomUUID();
        UUID sub2 = UUID.randomUUID();
        UUID viewId = UUID.randomUUID();

        tracker.updateMembership(sub1, Map.of(viewId, "V"));
        tracker.updateMembership(sub2, Map.of(viewId, "V"));

        var result = tracker.getLastKnownMembership(Set.of(sub1, sub2));
        assertEquals(2, result.size());
    }

    @Test
    void removeMembership() {
        UUID subjectId = UUID.randomUUID();
        tracker.updateMembership(subjectId, Map.of(UUID.randomUUID(), "V"));

        tracker.removeMembership(subjectId);
        assertTrue(tracker.getLastKnownMembership(subjectId).isEmpty());
    }

    @Test
    void getSubjectsByView() {
        UUID viewId = UUID.randomUUID();
        UUID sub1 = UUID.randomUUID();
        UUID sub2 = UUID.randomUUID();

        tracker.updateMembership(sub1, Map.of(viewId, "V"));
        tracker.updateMembership(sub2, Map.of(viewId, "V"));

        Set<UUID> subjects = tracker.getSubjectsByView(viewId);
        assertEquals(2, subjects.size());
    }

    @Test
    void removeMembershipByView() {
        UUID viewId = UUID.randomUUID();
        UUID sub1 = UUID.randomUUID();

        tracker.updateMembership(sub1, Map.of(viewId, "V"));
        tracker.removeMembershipByView(viewId);

        assertTrue(tracker.getSubjectsByView(viewId).isEmpty());
    }

    @Test
    void updateMembershipReplacesExisting() {
        UUID subjectId = UUID.randomUUID();
        UUID viewId1 = UUID.randomUUID();
        UUID viewId2 = UUID.randomUUID();

        tracker.updateMembership(subjectId, Map.of(viewId1, "Old"));
        tracker.updateMembership(subjectId, Map.of(viewId2, "New"));

        Map<UUID, String> membership = tracker.getLastKnownMembership(subjectId);
        assertEquals(1, membership.size());
        assertEquals("New", membership.get(viewId2));
    }
}
