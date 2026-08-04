package io.casehub.platform.preferences;

import io.casehub.platform.api.preferences.PlatformPreferenceKeys;
import io.casehub.platform.api.preferences.PreferenceConstraintKeys;
import io.casehub.platform.api.preferences.PreferenceSchemaDescriptor;
import io.casehub.platform.api.preferences.PreferenceSchemaRegistry;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import java.util.Map;

@ApplicationScoped
public class PlatformPreferenceRegistrar {

    @Inject
    PreferenceSchemaRegistry registry;

    void onStart(@Observes StartupEvent event) {
        registry.register(PreferenceSchemaDescriptor.of(PlatformPreferenceKeys.NOTIFICATION_RETENTION_DAYS)
                .label("Notification retention (days)")
                .description("Days to retain read and dismissed notifications before purge")
                .constraints(Map.of(PreferenceConstraintKeys.MIN, 1, PreferenceConstraintKeys.MAX, 3650))
                .build());

        registry.register(PreferenceSchemaDescriptor.of(PlatformPreferenceKeys.NOTIFICATION_UNREAD_RETENTION_DAYS)
                .label("Unread notification retention (days)")
                .description("Days to retain unread notifications before purge")
                .constraints(Map.of(PreferenceConstraintKeys.MIN, 1, PreferenceConstraintKeys.MAX, 3650))
                .build());

        registry.register(PreferenceSchemaDescriptor.of(PlatformPreferenceKeys.ACL_AUDIT_RETENTION_DAYS)
                .label("ACL audit log retention (days)")
                .description("Days to retain ACL audit log entries before purge")
                .constraints(Map.of(PreferenceConstraintKeys.MIN, 30, PreferenceConstraintKeys.MAX, 3650))
                .build());

        registry.register(PreferenceSchemaDescriptor.of(PlatformPreferenceKeys.DELIVERY_ATTEMPT_RETENTION_DAYS)
                .label("Delivery attempt retention (days)")
                .description("Days to retain delivered and expired delivery attempts before purge")
                .constraints(Map.of(PreferenceConstraintKeys.MIN, 1, PreferenceConstraintKeys.MAX, 3650))
                .build());

        registry.register(PreferenceSchemaDescriptor.of(PlatformPreferenceKeys.DELIVERY_FAILED_RETENTION_DAYS)
                .label("Failed delivery retention (days)")
                .description("Days to retain failed delivery attempts before purge")
                .constraints(Map.of(PreferenceConstraintKeys.MIN, 30, PreferenceConstraintKeys.MAX, 3650))
                .build());

        registry.register(PreferenceSchemaDescriptor.of(PlatformPreferenceKeys.DELIVERY_ENGAGEMENT_RETENTION_DAYS)
                .label("Engagement event retention (days)")
                .description("Days to retain engagement events before purge")
                .constraints(Map.of(PreferenceConstraintKeys.MIN, 1, PreferenceConstraintKeys.MAX, 3650))
                .build());

        registry.register(PreferenceSchemaDescriptor.of(PlatformPreferenceKeys.ENGAGEMENT_ENABLED)
                .label("Engagement tracking")
                .description("Enable engagement event recording for delivery tracking")
                .build());

        registry.register(PreferenceSchemaDescriptor.of(PlatformPreferenceKeys.DELIVERY_RETRY_MAX_RETRIES)
                .label("Delivery retry limit")
                .description("Maximum retry attempts before marking delivery as expired")
                .constraints(Map.of(PreferenceConstraintKeys.MIN, 0, PreferenceConstraintKeys.MAX, 20))
                .build());

        registry.register(PreferenceSchemaDescriptor.of(PlatformPreferenceKeys.DIGEST_RETENTION_DAYS)
                .label("Digest buffer retention (days)")
                .description("Days to retain digest buffer entries before purge")
                .constraints(Map.of(PreferenceConstraintKeys.MIN, 1, PreferenceConstraintKeys.MAX, 3650))
                .build());

        registry.register(PreferenceSchemaDescriptor.of(PlatformPreferenceKeys.VIEW_CACHE_TTL_SECONDS)
                .label("View cache TTL (seconds)")
                .description("Time-to-live for cached view definitions (0 = disabled)")
                .constraints(Map.of(PreferenceConstraintKeys.MIN, 0, PreferenceConstraintKeys.MAX, 3600))
                .build());
    }
}
