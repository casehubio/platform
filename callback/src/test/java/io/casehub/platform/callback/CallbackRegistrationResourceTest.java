package io.casehub.platform.callback;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

@QuarkusTest
class CallbackRegistrationResourceTest {

    @Test
    void register_returnsRegistrationWithId() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "spiName": "worker-provisioner",
                    "callbackUrl": "http://app1/casehub/callbacks/worker-provisioner",
                    "tenancyId": "tenant-1",
                    "timeoutMs": 5000,
                    "ttlSeconds": 300
                }
                """)
        .when()
            .post("/casehub/callbacks/register")
        .then()
            .statusCode(200)
            .body("id", notNullValue())
            .body("spiName", equalTo("worker-provisioner"))
            .body("callbackUrl", equalTo("http://app1/casehub/callbacks/worker-provisioner"))
            .body("tenancyId", equalTo("tenant-1"));
    }

    @Test
    void heartbeat_existingRegistration_returns204() {
        String id = given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "spiName": "worker-provisioner",
                    "callbackUrl": "http://app2/casehub/callbacks/worker-provisioner",
                    "tenancyId": "tenant-1",
                    "timeoutMs": 5000,
                    "ttlSeconds": 300
                }
                """)
        .when()
            .post("/casehub/callbacks/register")
        .then()
            .statusCode(200)
            .extract().path("id");

        given()
        .when()
            .put("/casehub/callbacks/" + id + "/heartbeat")
        .then()
            .statusCode(204);
    }

    @Test
    void heartbeat_unknownId_returns404() {
        given()
        .when()
            .put("/casehub/callbacks/nonexistent/heartbeat")
        .then()
            .statusCode(404);
    }

    @Test
    void deregister_existingRegistration_returns204() {
        String id = given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "spiName": "worker-provisioner",
                    "callbackUrl": "http://app3/casehub/callbacks/worker-provisioner",
                    "tenancyId": "tenant-1",
                    "timeoutMs": 5000,
                    "ttlSeconds": 300
                }
                """)
        .when()
            .post("/casehub/callbacks/register")
        .then()
            .statusCode(200)
            .extract().path("id");

        given()
        .when()
            .delete("/casehub/callbacks/" + id)
        .then()
            .statusCode(204);
    }

    @Test
    void deregister_unknownId_returns204() {
        given()
        .when()
            .delete("/casehub/callbacks/nonexistent")
        .then()
            .statusCode(204);
    }
}
