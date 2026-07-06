package io.casehub.platform.notification.rest;

import io.casehub.platform.api.delivery.DeliveryChannelDescriptor;
import io.casehub.platform.api.delivery.DeliveryChannelRegistry;
import io.casehub.platform.api.delivery.DeliveryResult;
import io.casehub.platform.api.delivery.NotificationDeliverer;
import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.platform.api.notification.NotificationInput;
import io.casehub.platform.api.notification.NotificationSeverity;
import io.casehub.platform.testing.FixedCurrentPrincipal;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

@QuarkusTest
class DeliveryChannelResourceTest {

    @Inject
    FixedCurrentPrincipal principal;

    @Inject
    DeliveryChannelRegistry registry;

    @BeforeEach
    void setUp() {
        principal.reset();
        principal.setActorId("user-1");
        principal.setTenancyId(TenancyConstants.DEFAULT_TENANT_ID);

        registry.register(
                new DeliveryChannelDescriptor("test_channel", "Test Channel", true, false, NotificationSeverity.WARNING),
                new TestDeliverer());
    }

    @Test
    void getChannels_returnsRegisteredChannels() {
        given()
            .when().get("/notifications/channels")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("$", hasSize(1))
            .body("[0].channelId", equalTo("test_channel"))
            .body("[0].displayName", equalTo("Test Channel"))
            .body("[0].external", equalTo(true));
    }

    private static class TestDeliverer implements NotificationDeliverer {
        @Override public String channelId() { return "test_channel"; }
        @Override public DeliveryResult deliver(NotificationInput notification) {
            return new DeliveryResult(true, null);
        }
    }
}
