package io.casehub.platform.api.notification;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationSeverityTest {

    @Test
    void isAtLeast_INFO_meetsINFO() {
        assertThat(NotificationSeverity.INFO.isAtLeast(NotificationSeverity.INFO)).isTrue();
    }

    @Test
    void isAtLeast_WARNING_meetsINFO() {
        assertThat(NotificationSeverity.WARNING.isAtLeast(NotificationSeverity.INFO)).isTrue();
    }

    @Test
    void isAtLeast_URGENT_meetsINFO() {
        assertThat(NotificationSeverity.URGENT.isAtLeast(NotificationSeverity.INFO)).isTrue();
    }

    @Test
    void isAtLeast_INFO_doesNotMeetWARNING() {
        assertThat(NotificationSeverity.INFO.isAtLeast(NotificationSeverity.WARNING)).isFalse();
    }

    @Test
    void isAtLeast_WARNING_meetsWARNING() {
        assertThat(NotificationSeverity.WARNING.isAtLeast(NotificationSeverity.WARNING)).isTrue();
    }

    @Test
    void isAtLeast_URGENT_meetsWARNING() {
        assertThat(NotificationSeverity.URGENT.isAtLeast(NotificationSeverity.WARNING)).isTrue();
    }

    @Test
    void isAtLeast_INFO_doesNotMeetURGENT() {
        assertThat(NotificationSeverity.INFO.isAtLeast(NotificationSeverity.URGENT)).isFalse();
    }

    @Test
    void isAtLeast_WARNING_doesNotMeetURGENT() {
        assertThat(NotificationSeverity.WARNING.isAtLeast(NotificationSeverity.URGENT)).isFalse();
    }

    @Test
    void isAtLeast_URGENT_meetsURGENT() {
        assertThat(NotificationSeverity.URGENT.isAtLeast(NotificationSeverity.URGENT)).isTrue();
    }
}
