package io.casehub.platform.api.preferences;

public final class PlatformPreferenceKeys {

    public static final PreferenceKey<IntPreference> NOTIFICATION_RETENTION_DAYS =
            new PreferenceKey<>("casehub.platform", "notification.retention-days",
                    IntPreference.of(90), IntPreference::parse);

    public static final PreferenceKey<IntPreference> NOTIFICATION_UNREAD_RETENTION_DAYS =
            new PreferenceKey<>("casehub.platform", "notification.unread-retention-days",
                    IntPreference.of(365), IntPreference::parse);

    public static final PreferenceKey<IntPreference> ACL_AUDIT_RETENTION_DAYS =
            new PreferenceKey<>("casehub.platform", "acl.audit-retention-days",
                    IntPreference.of(365), IntPreference::parse);

    public static final PreferenceKey<IntPreference> DELIVERY_ATTEMPT_RETENTION_DAYS =
            new PreferenceKey<>("casehub.platform", "delivery.attempt-retention-days",
                    IntPreference.of(30), IntPreference::parse);

    public static final PreferenceKey<IntPreference> DELIVERY_FAILED_RETENTION_DAYS =
            new PreferenceKey<>("casehub.platform", "delivery.failed-retention-days",
                    IntPreference.of(365), IntPreference::parse);

    public static final PreferenceKey<IntPreference> DELIVERY_ENGAGEMENT_RETENTION_DAYS =
            new PreferenceKey<>("casehub.platform", "delivery.engagement-retention-days",
                    IntPreference.of(90), IntPreference::parse);

    private PlatformPreferenceKeys() {}
}
