package io.casehub.platform.notification.rest;

import io.casehub.platform.api.delivery.DigestBuffer;
import io.casehub.platform.api.delivery.DigestBufferKey;
import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.platform.api.notification.NotificationInput;
import io.casehub.platform.api.notification.NotificationSeverity;
import io.casehub.platform.api.notification.NotificationSource;
import io.casehub.platform.testing.FixedCurrentPrincipal;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

/**
 * Tests for {@link DigestStatusResource}.
 *
 * <p>Uses {@link InMemoryDigestBuffer} from notification-dispatch (test dep).
 */
@QuarkusTest
class DigestStatusResourceTest {

    @Inject
    FixedCurrentPrincipal principal;

    @Inject
    DigestBuffer digestBuffer;

    @BeforeEach
    void setUp() {
        principal.reset();
        principal.setActorId("user-1");
        principal.setTenancyId(TenancyConstants.DEFAULT_TENANT_ID);
        // Drain any existing buffers for the test user
        digestBuffer.pendingKeysForUser("user-1", TenancyConstants.DEFAULT_TENANT_ID)
                .forEach(digestBuffer::drain);
    }

    @Test
    void digestStatus_returnsPendingCountsPerChannel() {
        // Arrange: add items to digest buffer for the test user
        var key1 = new DigestBufferKey("user-1", TenancyConstants.DEFAULT_TENANT_ID, "email");
        var key2 = new DigestBufferKey("user-1", TenancyConstants.DEFAULT_TENANT_ID, "sms");

        var notification1 = new NotificationInput(
            "user-1", TenancyConstants.DEFAULT_TENANT_ID,
            "Title 1", "Body 1", "category-1", NotificationSeverity.INFO,
            null, new NotificationSource("event-1", "case", "case-1", "actor-1")
        );
        var notification2 = new NotificationInput(
            "user-1", TenancyConstants.DEFAULT_TENANT_ID,
            "Title 2", "Body 2", "category-2", NotificationSeverity.INFO,
            null, new NotificationSource("event-2", "case", "case-2", "actor-2")
        );
        var notification3 = new NotificationInput(
            "user-1", TenancyConstants.DEFAULT_TENANT_ID,
            "Title 3", "Body 3", "category-3", NotificationSeverity.INFO,
            null, new NotificationSource("event-3", "case", "case-3", "actor-3")
        );

        digestBuffer.add(key1, notification1);
        digestBuffer.add(key1, notification2);
        digestBuffer.add(key2, notification3);

        // Act & Assert
        given()
            .when().get("/notifications/digest/status")
            .then()
            .statusCode(200)
            .body("email", equalTo(2))
            .body("sms", equalTo(1));
    }

    @Test
    void digestStatus_returnsEmptyMap_whenNoPending() {
        given()
            .when().get("/notifications/digest/status")
            .then()
            .statusCode(200)
            .body("size()", is(0));
    }

    @Test
    void digestStatus_tenantIsolation_doesNotShowOtherTenantsData() {
        // Arrange: add notifications for tenant-2
        var keyOtherTenant = new DigestBufferKey("user-1", "tenant-2", "email");
        var notification = new NotificationInput(
            "user-1", "tenant-2",
            "Title", "Body", "category", NotificationSeverity.INFO,
            null, new NotificationSource("event-1", "case", "case-1", "actor-1")
        );
        digestBuffer.add(keyOtherTenant, notification);

        // Act: user-1 in default tenant queries
        given()
            .when().get("/notifications/digest/status")
            .then()
            .statusCode(200)
            .body("size()", is(0));
    }

    @Test
    void digestStatus_userIsolation_doesNotShowOtherUsersData() {
        // Arrange: add notifications for user-2 in same tenant
        var keyOtherUser = new DigestBufferKey("user-2", TenancyConstants.DEFAULT_TENANT_ID, "email");
        var notification = new NotificationInput(
            "user-2", TenancyConstants.DEFAULT_TENANT_ID,
            "Title", "Body", "category", NotificationSeverity.INFO,
            null, new NotificationSource("event-1", "case", "case-1", "actor-1")
        );
        digestBuffer.add(keyOtherUser, notification);

        // Act: user-1 queries
        given()
            .when().get("/notifications/digest/status")
            .then()
            .statusCode(200)
            .body("size()", is(0));
    }
}
