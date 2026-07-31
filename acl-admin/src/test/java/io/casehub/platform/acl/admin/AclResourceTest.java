package io.casehub.platform.acl.admin;

import io.casehub.platform.api.acl.AccessControlProvider;
import io.casehub.platform.api.acl.AclAction;
import io.casehub.platform.testing.FixedCurrentPrincipal;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;

@QuarkusTest
@TestSecurity(user = "actor1", roles = "admin")
class AclResourceTest {

    @Inject
    AccessControlProvider acl;

    @Inject
    FixedCurrentPrincipal principal;

    @BeforeEach
    void setUp() {
        principal.reset();
        principal.setActorId("actor1");
        principal.addGroup("admin");
        acl.revokeAll("actor1", "case:abc");
        acl.revokeAll("actor1", "case:def");
        acl.revokeAll("actor1", "case:*");
        acl.revokeAll("actor1", "planitem:child");
    }

    // --- Grant endpoints ---

    @Test
    void grant_createsEntry() {
        given()
                .contentType("application/json")
                .body(Map.of("actorId", "actor1", "resourceId", "case:abc", "action", "READ"))
                .when().post("/acl/grants")
                .then().statusCode(204);

        given()
                .queryParam("actorId", "actor1")
                .queryParam("resourceId", "case:abc")
                .queryParam("action", "READ")
                .when().get("/acl/check")
                .then().statusCode(200)
                .body("allowed", equalTo(true));
    }

    @Test
    void grantBatch_createsMultipleEntries() {
        given()
                .contentType("application/json")
                .body(List.of(
                        Map.of("actorId", "actor1", "resourceId", "case:abc", "action", "READ"),
                        Map.of("actorId", "actor1", "resourceId", "case:def", "action", "WRITE")))
                .when().post("/acl/grants/batch")
                .then().statusCode(204);

        given()
                .queryParam("actorId", "actor1")
                .queryParam("resourceId", "case:abc")
                .queryParam("action", "READ")
                .when().get("/acl/check")
                .then().statusCode(200)
                .body("allowed", equalTo(true));

        given()
                .queryParam("actorId", "actor1")
                .queryParam("resourceId", "case:def")
                .queryParam("action", "WRITE")
                .when().get("/acl/check")
                .then().statusCode(200)
                .body("allowed", equalTo(true));
    }

    @Test
    void revoke_removesEntry() {
        acl.grant("actor1", "case:abc", AclAction.READ, null);

        given()
                .queryParam("actorId", "actor1")
                .queryParam("resourceId", "case:abc")
                .queryParam("action", "READ")
                .when().delete("/acl/grants")
                .then().statusCode(204);

        given()
                .queryParam("actorId", "actor1")
                .queryParam("resourceId", "case:abc")
                .queryParam("action", "READ")
                .when().get("/acl/check")
                .then().statusCode(200)
                .body("allowed", equalTo(false));
    }

    @Test
    void revokeBatch_removesMultipleEntries() {
        acl.grant("actor1", "case:abc", AclAction.READ, null);
        acl.grant("actor1", "case:def", AclAction.WRITE, null);

        given()
                .contentType("application/json")
                .body(List.of(
                        Map.of("actorId", "actor1", "resourceId", "case:abc", "action", "READ"),
                        Map.of("actorId", "actor1", "resourceId", "case:def", "action", "WRITE")))
                .when().delete("/acl/grants/batch")
                .then().statusCode(204);

        given()
                .queryParam("actorId", "actor1")
                .queryParam("resourceId", "case:abc")
                .queryParam("action", "READ")
                .when().get("/acl/check")
                .then().statusCode(200)
                .body("allowed", equalTo(false));
    }

    @Test
    void revokeAll_clearsBothGrantsAndDenies() {
        acl.grant("actor1", "case:abc", AclAction.READ, null);
        acl.deny("actor1", "case:abc", AclAction.WRITE, null);

        given()
                .queryParam("actorId", "actor1")
                .queryParam("resourceId", "case:abc")
                .when().delete("/acl/grants/all")
                .then().statusCode(204);

        acl.grant("actor1", "case:abc", AclAction.READ, null);
        given()
                .queryParam("actorId", "actor1")
                .queryParam("resourceId", "case:abc")
                .queryParam("action", "READ")
                .when().get("/acl/check")
                .then().statusCode(200)
                .body("allowed", equalTo(true));
    }

    // --- Deny endpoints ---

    @Test
    void deny_createsEntry() {
        acl.grant("actor1", "case:abc", AclAction.READ, null);

        given()
                .contentType("application/json")
                .body(Map.of("actorId", "actor1", "resourceId", "case:abc", "action", "READ"))
                .when().post("/acl/denies")
                .then().statusCode(204);

        given()
                .queryParam("actorId", "actor1")
                .queryParam("resourceId", "case:abc")
                .queryParam("action", "READ")
                .when().get("/acl/check")
                .then().statusCode(200)
                .body("allowed", equalTo(false));
    }

    @Test
    void denyBatch_createsMultipleDenies() {
        acl.grant("actor1", "case:abc", AclAction.READ, null);
        acl.grant("actor1", "case:def", AclAction.WRITE, null);

        given()
                .contentType("application/json")
                .body(List.of(
                        Map.of("actorId", "actor1", "resourceId", "case:abc", "action", "READ"),
                        Map.of("actorId", "actor1", "resourceId", "case:def", "action", "WRITE")))
                .when().post("/acl/denies/batch")
                .then().statusCode(204);

        given()
                .queryParam("actorId", "actor1")
                .queryParam("resourceId", "case:abc")
                .queryParam("action", "READ")
                .when().get("/acl/check")
                .then().statusCode(200)
                .body("allowed", equalTo(false));
    }

    @Test
    void removeDeny_removesEntry() {
        acl.grant("actor1", "case:abc", AclAction.READ, null);
        acl.deny("actor1", "case:abc", AclAction.READ, null);

        given()
                .queryParam("actorId", "actor1")
                .queryParam("resourceId", "case:abc")
                .queryParam("action", "READ")
                .when().delete("/acl/denies")
                .then().statusCode(204);

        given()
                .queryParam("actorId", "actor1")
                .queryParam("resourceId", "case:abc")
                .queryParam("action", "READ")
                .when().get("/acl/check")
                .then().statusCode(200)
                .body("allowed", equalTo(true));
    }

    @Test
    void removeDenyBatch_removesMultipleDenies() {
        acl.grant("actor1", "case:abc", AclAction.READ, null);
        acl.grant("actor1", "case:def", AclAction.WRITE, null);
        acl.deny("actor1", "case:abc", AclAction.READ, null);
        acl.deny("actor1", "case:def", AclAction.WRITE, null);

        given()
                .contentType("application/json")
                .body(List.of(
                        Map.of("actorId", "actor1", "resourceId", "case:abc", "action", "READ"),
                        Map.of("actorId", "actor1", "resourceId", "case:def", "action", "WRITE")))
                .when().delete("/acl/denies/batch")
                .then().statusCode(204);

        given()
                .queryParam("actorId", "actor1")
                .queryParam("resourceId", "case:abc")
                .queryParam("action", "READ")
                .when().get("/acl/check")
                .then().statusCode(200)
                .body("allowed", equalTo(true));
    }

    // --- Parents ---

    @Test
    void registerParent_createsRelationship() {
        acl.grant("actor1", "case:abc", AclAction.READ, null);

        given()
                .contentType("application/json")
                .body(Map.of("childResourceId", "planitem:child", "parentResourceId", "case:abc"))
                .when().post("/acl/parents")
                .then().statusCode(204);

        given()
                .queryParam("actorId", "actor1")
                .queryParam("resourceId", "planitem:child")
                .queryParam("action", "READ")
                .when().get("/acl/check")
                .then().statusCode(200)
                .body("allowed", equalTo(true));
    }

    // --- Check ---

    @Test
    void check_noGrant_returnsFalse() {
        given()
                .queryParam("actorId", "actor1")
                .queryParam("resourceId", "case:abc")
                .queryParam("action", "READ")
                .when().get("/acl/check")
                .then().statusCode(200)
                .body("allowed", equalTo(false));
    }

    @Test
    void check_missingParams_returns400() {
        given()
                .queryParam("actorId", "actor1")
                .when().get("/acl/check")
                .then().statusCode(400);
    }

    @Test
    @TestSecurity(user = "nonadmin")
    void check_nonAdminQueryingSelf_allowed() {
        principal.setActorId("nonadmin");
        principal.setGroups(java.util.Set.of());
        acl.grant("nonadmin", "case:abc", AclAction.READ, null);

        given()
                .queryParam("actorId", "nonadmin")
                .queryParam("resourceId", "case:abc")
                .queryParam("action", "READ")
                .when().get("/acl/check")
                .then().statusCode(200)
                .body("allowed", equalTo(true));
    }

    @Test
    @TestSecurity(user = "nonadmin")
    void check_nonAdminQueryingOtherActor_returns403() {
        principal.setActorId("nonadmin");
        principal.setGroups(java.util.Set.of());

        given()
                .queryParam("actorId", "actor1")
                .queryParam("resourceId", "case:abc")
                .queryParam("action", "READ")
                .when().get("/acl/check")
                .then().statusCode(403);
    }

    // --- Accessible ---

    @Test
    void accessible_returnsPaginatedResults() {
        acl.grant("actor1", "case:abc", AclAction.READ, null);
        acl.grant("actor1", "case:def", AclAction.READ, null);

        given()
                .queryParam("actorId", "actor1")
                .queryParam("resourceType", "case")
                .queryParam("action", "READ")
                .when().get("/acl/accessible")
                .then().statusCode(200)
                .body("resourceIds", hasSize(2))
                .body("resourceIds", containsInAnyOrder("case:abc", "case:def"));
    }

    @Test
    void accessible_wildcardGrant_includesWildcard() {
        acl.grant("actor1", "case:*", AclAction.READ, null);

        given()
                .queryParam("actorId", "actor1")
                .queryParam("resourceType", "case")
                .queryParam("action", "READ")
                .when().get("/acl/accessible")
                .then().statusCode(200)
                .body("resourceIds", hasItem("case:*"));
    }

    @Test
    void accessible_missingParams_returns400() {
        given()
                .queryParam("actorId", "actor1")
                .when().get("/acl/accessible")
                .then().statusCode(400);
    }

    @Test
    @TestSecurity(user = "nonadmin")
    void accessible_nonAdminQueryingOtherActor_returns403() {
        principal.setActorId("nonadmin");
        principal.setGroups(java.util.Set.of());

        given()
                .queryParam("actorId", "actor1")
                .queryParam("resourceType", "case")
                .queryParam("action", "READ")
                .when().get("/acl/accessible")
                .then().statusCode(403);
    }

    // --- Validation ---

    @Test
    void revoke_missingParams_returns400() {
        given()
                .queryParam("actorId", "actor1")
                .when().delete("/acl/grants")
                .then().statusCode(400);
    }

    @Test
    void revokeAll_missingParams_returns400() {
        given()
                .queryParam("actorId", "actor1")
                .when().delete("/acl/grants/all")
                .then().statusCode(400);
    }

    @Test
    void removeDeny_missingParams_returns400() {
        given()
                .queryParam("actorId", "actor1")
                .when().delete("/acl/denies")
                .then().statusCode(400);
    }
}
