package io.casehub.platform.notification.rest;

import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.platform.api.notification.NotificationSeverity;
import io.casehub.platform.api.notification.settings.ChannelPreference;
import io.casehub.platform.api.notification.settings.NotificationPreferenceStore;
import io.casehub.platform.api.notification.settings.NotificationPreferenceUpdate;
import io.casehub.platform.api.notification.settings.QuietHours;
import io.casehub.platform.notification.settings.inmem.InMemoryNotificationPreferenceStore;
import io.casehub.platform.testing.FixedCurrentPrincipal;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

/**
 * Tests for {@link NotificationPreferenceResource}.
 *
 * <p>Uses in-memory store from notification-settings-inmem (test dep).
 */
@QuarkusTest
class NotificationPreferenceResourceTest {

    @Inject
    FixedCurrentPrincipal principal;

    @Inject
    NotificationPreferenceStore store;

    @Inject
    InMemoryNotificationPreferenceStore inMemoryStore;

    @BeforeEach
    void setUp() {
        principal.reset();
        principal.setActorId("user-1");
        principal.setTenancyId(TenancyConstants.DEFAULT_TENANT_ID);
        inMemoryStore.clear();
    }

    @Test
    void get_returnsEmptyPreferences_whenNoneStored() {
        given()
            .when().get("/notifications/preferences")
            .then()
            .statusCode(200)
            .body("userId", equalTo("user-1"))
            .body("tenancyId", equalTo(TenancyConstants.DEFAULT_TENANT_ID))
            .body("quietHours", nullValue())
            .body("updatedAt", equalTo(Instant.EPOCH.toString()));
    }

    @Test
    void put_storesAndReturnsPreferences() {
        var update = new NotificationPreferenceUpdate(
            Map.of("email", new ChannelPreference(true, NotificationSeverity.WARNING, null, null)),
            new QuietHours(LocalTime.of(22, 0), LocalTime.of(7, 0), ZoneId.of("America/New_York"), null),
            false
        );

        given()
            .contentType(ContentType.JSON)
            .body(update)
            .when().put("/notifications/preferences")
            .then()
            .statusCode(200)
            .body("userId", equalTo("user-1"))
            .body("tenancyId", equalTo(TenancyConstants.DEFAULT_TENANT_ID))
            .body("channelDefaults.email.enabled", is(true))
            .body("channelDefaults.email.minSeverity", equalTo("WARNING"))
            .body("quietHours.start", equalTo("22:00:00"))
            .body("quietHours.end", equalTo("07:00:00"))
            .body("quietHours.timezone", equalTo("America/New_York"))
            .body("updatedAt", notNullValue());

        // Verify GET returns the same
        given()
            .when().get("/notifications/preferences")
            .then()
            .statusCode(200)
            .body("channelDefaults.email.enabled", is(true))
            .body("quietHours.start", equalTo("22:00:00"));
    }

    @Test
    void put_clearQuietHours_removesQuietHours() {
        // First, set quiet hours
        var setUpdate = new NotificationPreferenceUpdate(
            null,
            new QuietHours(LocalTime.of(22, 0), LocalTime.of(7, 0), ZoneId.of("UTC"), null),
            false
        );
        given()
            .contentType(ContentType.JSON)
            .body(setUpdate)
            .when().put("/notifications/preferences")
            .then()
            .statusCode(200)
            .body("quietHours", notNullValue());

        // Clear quiet hours
        var clearUpdate = new NotificationPreferenceUpdate(null, null, true);
        given()
            .contentType(ContentType.JSON)
            .body(clearUpdate)
            .when().put("/notifications/preferences")
            .then()
            .statusCode(200)
            .body("quietHours", nullValue());

        // Verify GET returns no quiet hours
        given()
            .when().get("/notifications/preferences")
            .then()
            .statusCode(200)
            .body("quietHours", nullValue());
    }

    @Test
    void tenantIsolation_userCannotAccessOtherTenantPreferences() {
        // user-1 in default tenant sets preferences
        var update = new NotificationPreferenceUpdate(
            Map.of("email", new ChannelPreference(true, NotificationSeverity.INFO, null, null)),
            null,
            false
        );
        given()
            .contentType(ContentType.JSON)
            .body(update)
            .when().put("/notifications/preferences")
            .then()
            .statusCode(200);

        // Switch to user-2 in tenant-2
        principal.setActorId("user-2");
        principal.setTenancyId("tenant-2");

        given()
            .when().get("/notifications/preferences")
            .then()
            .statusCode(200)
            .body("tenancyId", equalTo("tenant-2"))
            .body("quietHours", nullValue()); // Empty, not user-1's preferences
    }

    @Test
    void put_overridesUserIdFromPrincipal() {
        // The REST layer should override userId/tenancyId from CurrentPrincipal
        var update = new NotificationPreferenceUpdate(
            Map.of("email", new ChannelPreference(true, NotificationSeverity.INFO, null, null)),
            null,
            false
        );

        given()
            .contentType(ContentType.JSON)
            .body(update)
            .when().put("/notifications/preferences")
            .then()
            .statusCode(200)
            .body("userId", equalTo("user-1"))
            .body("tenancyId", equalTo(TenancyConstants.DEFAULT_TENANT_ID));
    }
}
