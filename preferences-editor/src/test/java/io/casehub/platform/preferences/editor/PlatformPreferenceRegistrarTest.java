package io.casehub.platform.preferences.editor;

import io.casehub.platform.api.preferences.PreferenceSchemaDescriptor;
import io.casehub.platform.api.preferences.PreferenceSchemaRegistry;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class PlatformPreferenceRegistrarTest {

    @Inject
    PreferenceSchemaRegistry registry;

    @Test
    void all_platform_keys_are_registered() {
        Set<PreferenceSchemaDescriptor> all = registry.discover();
        var qualifiedNames = all.stream()
                                .map(PreferenceSchemaDescriptor::qualifiedName)
                                .filter(n -> n.startsWith("casehub.platform."))
                                .toList();

        assertTrue(qualifiedNames.contains("casehub.platform.notification.retention-days"));
        assertTrue(qualifiedNames.contains("casehub.platform.notification.unread-retention-days"));
        assertTrue(qualifiedNames.contains("casehub.platform.acl.audit-retention-days"));
        assertTrue(qualifiedNames.contains("casehub.platform.delivery.attempt-retention-days"));
        assertTrue(qualifiedNames.contains("casehub.platform.delivery.failed-retention-days"));
        assertTrue(qualifiedNames.contains("casehub.platform.delivery.engagement-retention-days"));
        assertTrue(qualifiedNames.contains("casehub.platform.delivery.engagement-enabled"));
        assertTrue(qualifiedNames.contains("casehub.platform.delivery.retry-max-retries"));
        assertTrue(qualifiedNames.contains("casehub.platform.notification.digest-retention-days"));
        assertTrue(qualifiedNames.contains("casehub.platform.view.cache-ttl-seconds"));
    }

    @Test
    void notification_retention_descriptor_has_correct_shape() {
        var descriptor = registry.resolve("casehub.platform.notification.retention-days");
        assertTrue(descriptor.isPresent());
        var d = descriptor.get();
        assertEquals("casehub.platform", d.namespace());
        assertEquals("notification.retention-days", d.name());
        assertEquals("integer", d.type());
        assertEquals("90", d.defaultValue());
        assertFalse(d.multiValue());
        assertNotNull(d.label());
        assertNotNull(d.description());
        assertEquals(1, d.constraints().get("min"));
        assertEquals(3650, d.constraints().get("max"));
    }

    @Test
    void engagement_enabled_descriptor_has_boolean_type() {
        var descriptor = registry.resolve("casehub.platform.delivery.engagement-enabled");
        assertTrue(descriptor.isPresent());
        var d = descriptor.get();
        assertEquals("casehub.platform", d.namespace());
        assertEquals("delivery.engagement-enabled", d.name());
        assertEquals("boolean", d.type());
        assertEquals("false", d.defaultValue());
        assertFalse(d.multiValue());
        assertNotNull(d.label());
        assertNotNull(d.description());
        assertTrue(d.constraints().isEmpty());
    }

    @Test
    void all_descriptors_have_labels_and_constraints() {
        var expectedKeys = Set.of(
                "casehub.platform.notification.retention-days",
                "casehub.platform.notification.unread-retention-days",
                "casehub.platform.acl.audit-retention-days",
                "casehub.platform.delivery.attempt-retention-days",
                "casehub.platform.delivery.failed-retention-days",
                "casehub.platform.delivery.engagement-retention-days",
                "casehub.platform.delivery.retry-max-retries",
                "casehub.platform.notification.digest-retention-days",
                "casehub.platform.view.cache-ttl-seconds"
                                 );
        for (var d : registry.discover()) {
            if (!expectedKeys.contains(d.qualifiedName())) {continue;}
            assertNotNull(d.label(), d.qualifiedName() + " missing label");
            assertFalse(d.constraints().isEmpty(), d.qualifiedName() + " missing constraints");
        }
    }
}
