package io.casehub.platform.notification.rest;

import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.platform.api.notification.settings.MuteRuleInput;
import io.casehub.platform.api.notification.settings.MuteScope;
import io.casehub.platform.api.notification.settings.SnoozeInput;
import io.casehub.platform.api.notification.settings.SuppressionStore;
import io.casehub.platform.notification.settings.inmem.InMemorySuppressionStore;
import io.casehub.platform.testing.FixedCurrentPrincipal;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

/**
 * Tests for {@link SuppressionResource}.
 *
 * <p>Uses in-memory store from notification-settings-inmem (test dep).
 */
@QuarkusTest
class SuppressionResourceTest {

    @Inject
    FixedCurrentPrincipal principal;

    @Inject
    SuppressionStore store;

    @Inject
    InMemorySuppressionStore inMemoryStore;

    @BeforeEach
    void setUp() {
        principal.reset();
        principal.setActorId("user-1");
        principal.setTenancyId(TenancyConstants.DEFAULT_TENANT_ID);
        inMemoryStore.clear();
    }

    // ===== Mute tests =====

    @Test
    void postMute_createsAndReturnsMuteRule() {
        var input = new MuteRuleInput(
            "user-1", // Will be overridden from principal
            TenancyConstants.DEFAULT_TENANT_ID,
            MuteScope.ENTITY,
            "work-item-123",
            "work-item",
            Instant.now().plus(7, ChronoUnit.DAYS)
        );

        given()
            .contentType(ContentType.JSON)
            .body(input)
            .when().post("/notifications/mute")
            .then()
            .statusCode(201)
            .body("id", notNullValue())
            .body("userId", equalTo("user-1"))
            .body("tenancyId", equalTo(TenancyConstants.DEFAULT_TENANT_ID))
            .body("scope", equalTo("ENTITY"))
            .body("scopeId", equalTo("work-item-123"))
            .body("entityType", equalTo("work-item"))
            .body("createdAt", notNullValue())
            .body("expiresAt", notNullValue());
    }

    @Test
    void getMute_returnsActiveMuteRules() {
        // Create two mute rules
        var input1 = new MuteRuleInput(
            "user-1",
            TenancyConstants.DEFAULT_TENANT_ID,
            MuteScope.ENTITY,
            "work-item-123",
            "work-item",
            null // Permanent
        );
        store.addMute(input1);

        var input2 = new MuteRuleInput(
            "user-1",
            TenancyConstants.DEFAULT_TENANT_ID,
            MuteScope.CATEGORY,
            "comment",
            null, // All entity types
            Instant.now().plus(7, ChronoUnit.DAYS)
        );
        store.addMute(input2);

        given()
            .when().get("/notifications/mute")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("$", hasSize(2));
    }

    @Test
    void deleteMute_removesRule_returns204() {
        var input = new MuteRuleInput(
            "user-1",
            TenancyConstants.DEFAULT_TENANT_ID,
            MuteScope.ENTITY,
            "work-item-123",
            "work-item",
            null
        );
        var rule = store.addMute(input);

        given()
            .when().delete("/notifications/mute/{id}", rule.id())
            .then()
            .statusCode(204);

        // Verify it's gone
        given()
            .when().get("/notifications/mute")
            .then()
            .statusCode(200)
            .body("$", hasSize(0));
    }

    @Test
    void deleteMute_returns404ForNonexistentRule() {
        given()
            .when().delete("/notifications/mute/{id}", "nonexistent-id")
            .then()
            .statusCode(404);
    }

    @Test
    void deleteMute_returns404ForOtherUsersMuteRule() {
        // Create a mute rule for user-1
        var input = new MuteRuleInput(
            "user-1",
            TenancyConstants.DEFAULT_TENANT_ID,
            MuteScope.ENTITY,
            "work-item-123",
            "work-item",
            null
        );
        var rule = store.addMute(input);

        // Switch to user-2
        principal.setActorId("user-2");

        // Try to delete user-1's rule
        given()
            .when().delete("/notifications/mute/{id}", rule.id())
            .then()
            .statusCode(404);
    }

    @Test
    void mute_tenantIsolation() {
        // user-1 in default tenant creates a mute rule
        var input = new MuteRuleInput(
            "user-1",
            TenancyConstants.DEFAULT_TENANT_ID,
            MuteScope.ENTITY,
            "work-item-123",
            "work-item",
            null
        );
        store.addMute(input);

        // Switch to user-2 in tenant-2
        principal.setActorId("user-2");
        principal.setTenancyId("tenant-2");

        // user-2 should not see user-1's mute rules
        given()
            .when().get("/notifications/mute")
            .then()
            .statusCode(200)
            .body("$", hasSize(0));
    }

    // ===== Snooze tests =====

    @Test
    void postSnooze_activatesSnooze() {
        var until = Instant.now().plus(2, ChronoUnit.HOURS);
        var input = new SnoozeInput(
            "user-1", // Will be overridden from principal
            TenancyConstants.DEFAULT_TENANT_ID,
            until
        );

        given()
            .contentType(ContentType.JSON)
            .body(input)
            .when().post("/notifications/snooze")
            .then()
            .statusCode(201)
            .body("userId", equalTo("user-1"))
            .body("tenancyId", equalTo(TenancyConstants.DEFAULT_TENANT_ID))
            .body("until", notNullValue())
            .body("createdAt", notNullValue());
    }

    @Test
    void postSnooze_replacesExistingSnooze() {
        // Activate first snooze
        var until1 = Instant.now().plus(1, ChronoUnit.HOURS);
        var input1 = new SnoozeInput("user-1", TenancyConstants.DEFAULT_TENANT_ID, until1);
        store.activateSnooze(input1);

        // Activate second snooze (replaces first)
        var until2 = Instant.now().plus(3, ChronoUnit.HOURS);
        var input2 = new SnoozeInput("user-1", TenancyConstants.DEFAULT_TENANT_ID, until2);

        given()
            .contentType(ContentType.JSON)
            .body(input2)
            .when().post("/notifications/snooze")
            .then()
            .statusCode(201)
            .body("until", notNullValue());

        // Verify GET returns the second snooze
        given()
            .when().get("/notifications/snooze")
            .then()
            .statusCode(200)
            .body("until", notNullValue());
    }

    @Test
    void getSnooze_returnsActiveSnooze() {
        var until = Instant.now().plus(2, ChronoUnit.HOURS);
        var input = new SnoozeInput("user-1", TenancyConstants.DEFAULT_TENANT_ID, until);
        store.activateSnooze(input);

        given()
            .when().get("/notifications/snooze")
            .then()
            .statusCode(200)
            .body("userId", equalTo("user-1"))
            .body("tenancyId", equalTo(TenancyConstants.DEFAULT_TENANT_ID))
            .body("until", notNullValue());
    }

    @Test
    void getSnooze_returns404WhenNoActiveSnooze() {
        given()
            .when().get("/notifications/snooze")
            .then()
            .statusCode(404);
    }

    @Test
    void deleteSnooze_cancelsSnooze_returns204() {
        var until = Instant.now().plus(2, ChronoUnit.HOURS);
        var input = new SnoozeInput("user-1", TenancyConstants.DEFAULT_TENANT_ID, until);
        store.activateSnooze(input);

        given()
            .when().delete("/notifications/snooze")
            .then()
            .statusCode(204);

        // Verify it's gone
        given()
            .when().get("/notifications/snooze")
            .then()
            .statusCode(404);
    }

    @Test
    void deleteSnooze_returns404WhenNoActiveSnooze() {
        given()
            .when().delete("/notifications/snooze")
            .then()
            .statusCode(404);
    }

    @Test
    void snooze_tenantIsolation() {
        // user-1 in default tenant activates snooze
        var until = Instant.now().plus(2, ChronoUnit.HOURS);
        var input = new SnoozeInput("user-1", TenancyConstants.DEFAULT_TENANT_ID, until);
        store.activateSnooze(input);

        // Switch to user-2 in tenant-2
        principal.setActorId("user-2");
        principal.setTenancyId("tenant-2");

        // user-2 should not see user-1's snooze
        given()
            .when().get("/notifications/snooze")
            .then()
            .statusCode(404);
    }
}
