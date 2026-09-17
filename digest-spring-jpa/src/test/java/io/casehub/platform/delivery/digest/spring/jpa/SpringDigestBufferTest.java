package io.casehub.platform.delivery.digest.spring.jpa;

import io.casehub.platform.api.delivery.DigestBufferKey;
import io.casehub.platform.api.notification.NotificationInput;
import io.casehub.platform.api.notification.NotificationSeverity;
import io.casehub.platform.api.notification.NotificationSource;
import io.casehub.platform.delivery.digest.jpa.DigestBufferEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import(SpringDigestBufferTest.TestConfig.class)
class SpringDigestBufferTest {

    private static final String TENANT = "test-tenant";
    private static final DigestBufferKey KEY = new DigestBufferKey("user-1", TENANT, "email");

    @Configuration
    @AutoConfigurationPackage
    @EnableJpaRepositories(basePackageClasses = DigestBufferEntityRepository.class)
    @EntityScan(basePackageClasses = DigestBufferEntity.class)
    static class TestConfig {
        @Bean
        SpringDigestBuffer springDigestBuffer(DigestBufferEntityRepository repo) {
            return new SpringDigestBuffer(repo, 0);
        }
    }

    @Autowired SpringDigestBuffer buffer;

    private NotificationInput testNotification(String title) {
        return new NotificationInput(
                "user-1", TENANT, title, null, "test.event",
                NotificationSeverity.INFO, null,
                new NotificationSource("evt-1", "case", "case-1", "actor-1"));
    }

    @Test
    void addAndDrain() {
        buffer.add(KEY, testNotification("Alert 1"));
        buffer.add(KEY, testNotification("Alert 2"));

        List<NotificationInput> drained = buffer.drain(KEY);
        assertEquals(2, drained.size());
        assertEquals("Alert 1", drained.get(0).title());
        assertEquals("Alert 2", drained.get(1).title());

        assertTrue(buffer.drain(KEY).isEmpty());
    }

    @Test
    void pendingKeys() {
        buffer.add(KEY, testNotification("A"));
        buffer.add(new DigestBufferKey("user-2", TENANT, "sms"), testNotification("B"));

        Set<DigestBufferKey> keys = buffer.pendingKeys();
        assertEquals(2, keys.size());
        assertTrue(keys.contains(KEY));
    }

    @Test
    void pendingCount() {
        buffer.add(KEY, testNotification("A"));
        buffer.add(KEY, testNotification("B"));

        assertEquals(2, buffer.pendingCount(KEY));
    }

    @Test
    void oldestPendingTimestamp() {
        buffer.add(KEY, testNotification("A"));

        assertTrue(buffer.oldestPendingTimestamp(KEY).isPresent());
        assertTrue(buffer.oldestPendingTimestamp(
                new DigestBufferKey("nobody", TENANT, "email")).isEmpty());
    }

    @Test
    void pendingKeysForUser() {
        buffer.add(KEY, testNotification("A"));
        buffer.add(new DigestBufferKey("user-1", TENANT, "sms"), testNotification("B"));
        buffer.add(new DigestBufferKey("user-2", TENANT, "email"), testNotification("C"));

        Set<DigestBufferKey> keys = buffer.pendingKeysForUser("user-1", TENANT);
        assertEquals(2, keys.size());
    }

    @Test
    void drainReturnsChronologicalOrder() {
        buffer.add(KEY, testNotification("First"));
        buffer.add(KEY, testNotification("Second"));
        buffer.add(KEY, testNotification("Third"));

        List<NotificationInput> drained = buffer.drain(KEY);
        assertEquals(3, drained.size());
        assertEquals("First", drained.get(0).title());
        assertEquals("Third", drained.get(2).title());
    }
}
