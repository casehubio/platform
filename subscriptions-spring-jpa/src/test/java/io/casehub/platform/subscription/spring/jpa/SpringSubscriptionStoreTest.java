package io.casehub.platform.subscription.spring.jpa;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.platform.api.notification.NotificationSeverity;
import io.casehub.platform.api.subscription.NotificationTarget;
import io.casehub.platform.api.subscription.NotificationTemplate;
import io.casehub.platform.api.subscription.Subscription;
import io.casehub.platform.api.subscription.SubscriptionInput;
import io.casehub.platform.api.subscription.SubscriptionPage;
import io.casehub.platform.api.subscription.SubscriptionQuery;
import io.casehub.platform.api.subscription.SubscriptionScope;
import io.casehub.platform.api.subscription.SubscriptionUpdate;
import io.casehub.platform.api.subscription.TargetType;
import io.casehub.platform.subscription.jpa.SubscriptionEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import(SpringSubscriptionStoreTest.TestConfig.class)
class SpringSubscriptionStoreTest {

    private static final String OWNER = "user-1";
    private static final String TENANT = "test-tenant";

    @Configuration
    @AutoConfigurationPackage
    @EnableJpaRepositories(basePackageClasses = SubscriptionEntityRepository.class)
    @EntityScan(basePackageClasses = SubscriptionEntity.class)
    static class TestConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }

        @Bean
        SpringSubscriptionStore springSubscriptionStore(
                SubscriptionEntityRepository repo,
                ObjectMapper mapper,
                ApplicationEventPublisher events) {
            return new SpringSubscriptionStore(repo, mapper, events);
        }
    }

    @Autowired SpringSubscriptionStore store;

    private SubscriptionInput testInput(String name) {
        return new SubscriptionInput(
                OWNER, TENANT, name, "test.event",
                List.of(),
                List.of(new NotificationTarget(TargetType.USER, OWNER)),
                false,
                new NotificationTemplate("{title}", null, NotificationSeverity.INFO,
                        "test", null, "case", "entityId", "actorId"),
                true,
                SubscriptionScope.USER);
    }

    @Test
    void storeAndFindById() {
        Subscription sub = store.store(testInput("Sub 1"));
        assertNotNull(sub.id());
        assertEquals("Sub 1", sub.name());

        Optional<Subscription> found = store.findById(sub.id(), OWNER, TENANT);
        assertTrue(found.isPresent());
        assertEquals("Sub 1", found.get().name());
    }

    @Test
    void findReturnsPage() {
        store.store(testInput("Sub 1"));
        store.store(testInput("Sub 2"));

        SubscriptionPage page = store.find(new SubscriptionQuery(OWNER, TENANT, SubscriptionScope.USER, null, null, 10));
        assertEquals(2, page.subscriptions().size());
    }

    @Test
    void update() {
        Subscription sub = store.store(testInput("Original"));
        Optional<Subscription> updated = store.update(sub.id(), OWNER, TENANT,
                new SubscriptionUpdate("Updated", null, null, null, null, null, null));

        assertTrue(updated.isPresent());
        assertEquals("Updated", updated.get().name());
    }

    @Test
    void delete() {
        Subscription sub = store.store(testInput("To Delete"));
        assertTrue(store.delete(sub.id(), OWNER, TENANT));
        assertTrue(store.findById(sub.id(), OWNER, TENANT).isEmpty());
    }

    @Test
    void deleteNonExistentReturnsFalse() {
        assertFalse(store.delete("nonexistent", OWNER, TENANT));
    }

    @Test
    void findAllEnabled() {
        store.store(testInput("Enabled"));
        store.store(new SubscriptionInput(
                OWNER, TENANT, "Disabled", "test.event",
                List.of(),
                List.of(new NotificationTarget(TargetType.USER, OWNER)),
                false,
                new NotificationTemplate("{title}", null, NotificationSeverity.INFO,
                        "test", null, "case", "entityId", "actorId"),
                false, SubscriptionScope.USER));

        List<Subscription> enabled = store.findAllEnabled().toList();
        assertEquals(1, enabled.size());
        assertEquals("Enabled", enabled.getFirst().name());
    }

    @Test
    void cursorPagination() {
        for (int i = 0; i < 5; i++) {
            store.store(testInput("Sub " + i));
        }

        SubscriptionPage page1 = store.find(new SubscriptionQuery(OWNER, TENANT, SubscriptionScope.USER, null, null, 3));
        assertEquals(3, page1.subscriptions().size());
        assertNotNull(page1.nextCursor());

        SubscriptionPage page2 = store.find(new SubscriptionQuery(OWNER, TENANT, SubscriptionScope.USER, null, page1.nextCursor(), 3));
        assertEquals(2, page2.subscriptions().size());
        assertNull(page2.nextCursor());
    }
}
