package io.casehub.platform.api.preferences;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class PlatformPreferenceKeysTest {

    static Stream<Arguments> keys() {
        return Stream.of(
            Arguments.of(PlatformPreferenceKeys.NOTIFICATION_RETENTION_DAYS,
                         "notification.retention-days", 90),
            Arguments.of(PlatformPreferenceKeys.NOTIFICATION_UNREAD_RETENTION_DAYS,
                         "notification.unread-retention-days", 365),
            Arguments.of(PlatformPreferenceKeys.ACL_AUDIT_RETENTION_DAYS,
                         "acl.audit-retention-days", 365),
            Arguments.of(PlatformPreferenceKeys.DELIVERY_ATTEMPT_RETENTION_DAYS,
                         "delivery.attempt-retention-days", 30),
            Arguments.of(PlatformPreferenceKeys.DELIVERY_FAILED_RETENTION_DAYS,
                         "delivery.failed-retention-days", 365),
            Arguments.of(PlatformPreferenceKeys.DELIVERY_ENGAGEMENT_RETENTION_DAYS,
                         "delivery.engagement-retention-days", 90)
        );
    }

    @ParameterizedTest
    @MethodSource("keys")
    void key_has_correct_namespace_and_qualifiedName(PreferenceKey<IntPreference> key,
                                                     String expectedName, int expectedDefault) {
        assertEquals("casehub.platform", key.namespace());
        assertEquals(expectedName, key.name());
        assertEquals("casehub.platform." + expectedName, key.qualifiedName());
    }

    @ParameterizedTest
    @MethodSource("keys")
    void key_has_correct_default(PreferenceKey<IntPreference> key,
                                 String expectedName, int expectedDefault) {
        assertEquals(expectedDefault, key.defaultValue().value());
    }

    @ParameterizedTest
    @MethodSource("keys")
    void key_parser_round_trips(PreferenceKey<IntPreference> key,
                                String expectedName, int expectedDefault) {
        IntPreference parsed = key.parse(String.valueOf(expectedDefault));
        assertEquals(expectedDefault, parsed.value());
    }

    @Test
    void all_keys_have_unique_qualifiedNames() {
        var names = keys().map(a -> ((PreferenceKey<?>) a.get()[0]).qualifiedName()).toList();
        assertEquals(names.size(), names.stream().distinct().count());
    }
}
