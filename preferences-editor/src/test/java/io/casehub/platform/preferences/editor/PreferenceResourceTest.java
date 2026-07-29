package io.casehub.platform.preferences.editor;

import io.casehub.platform.api.preferences.IntPreference;
import io.casehub.platform.api.preferences.PreferenceConstraintKeys;
import io.casehub.platform.api.preferences.PreferenceKey;
import io.casehub.platform.api.preferences.PreferenceSchemaDescriptor;
import io.casehub.platform.api.preferences.PreferenceSchemaRegistry;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

@QuarkusTest
class PreferenceResourceTest {

    @Inject PreferenceSchemaRegistry schemaRegistry;

    @BeforeEach
    void registerSchemas() {
        PreferenceSchemaDescriptor intSchema = PreferenceSchemaDescriptor.of(
                new PreferenceKey<>("test", "count", IntPreference.of(0), IntPreference::parse))
                .label("Count")
                .constraints(Map.of(
                        PreferenceConstraintKeys.MIN, 1,
                        PreferenceConstraintKeys.MAX, 100))
                .build();
        schemaRegistry.register(intSchema);
    }

    @Test
    void set_returns_400_when_value_violates_schema() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {"namespace": "test", "name": "count", "subKey": "", "value": "999"}
                """)
            .queryParam("scope", "casehubio/validation")
        .when()
            .put("/preferences")
        .then()
            .statusCode(400)
            .body("violations", hasSize(1));
    }

    @Test
    void set_returns_400_when_value_not_parseable() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {"namespace": "test", "name": "count", "subKey": "", "value": "abc"}
                """)
            .queryParam("scope", "casehubio/validation")
        .when()
            .put("/preferences")
        .then()
            .statusCode(400)
            .body("violations", hasSize(1));
    }

    @Test
    void set_succeeds_when_value_passes_schema() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {"namespace": "test", "name": "count", "subKey": "", "value": "50"}
                """)
            .queryParam("scope", "casehubio/validation-ok")
        .when()
            .put("/preferences")
        .then()
            .statusCode(204);
    }

    @Test
    void set_succeeds_when_no_schema_registered() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {"namespace": "unknown", "name": "anything", "subKey": "", "value": "whatever"}
                """)
            .queryParam("scope", "casehubio/no-schema")
        .when()
            .put("/preferences")
        .then()
            .statusCode(204);
    }

    @Test
    void set_and_list_preference() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {"namespace": "test", "name": "count", "subKey": "", "value": "42"}
                """)
            .queryParam("scope", "casehubio/devtown")
        .when()
            .put("/preferences")
        .then()
            .statusCode(204);

        given()
            .queryParam("scope", "casehubio/devtown")
        .when()
            .get("/preferences")
        .then()
            .statusCode(200)
            .body("size()", is(1))
            .body("[0].name", is("count"))
            .body("[0].value", is("42"));
    }

    @Test
    void delete_single_preference() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {"namespace": "test", "name": "toDelete", "subKey": "", "value": "x"}
                """)
            .queryParam("scope", "casehubio/del")
        .when()
            .put("/preferences")
        .then()
            .statusCode(204);

        given()
            .queryParam("scope", "casehubio/del")
            .queryParam("namespace", "test")
            .queryParam("name", "toDelete")
        .when()
            .delete("/preferences")
        .then()
            .statusCode(204);

        given()
            .queryParam("scope", "casehubio/del")
        .when()
            .get("/preferences")
        .then()
            .statusCode(200)
            .body("size()", is(0));
    }

    @Test
    void delete_without_name_returns_400() {
        given()
            .queryParam("scope", "casehubio")
            .queryParam("namespace", "test")
        .when()
            .delete("/preferences")
        .then()
            .statusCode(400);
    }

    @Test
    void delete_namespace() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {"namespace": "bulk", "name": "a", "subKey": "", "value": "1"}
                """)
            .queryParam("scope", "casehubio/bulk")
        .when()
            .put("/preferences")
        .then()
            .statusCode(204);

        given()
            .queryParam("scope", "casehubio/bulk")
            .queryParam("namespace", "bulk")
        .when()
            .delete("/preferences/by-namespace")
        .then()
            .statusCode(204);

        given()
            .queryParam("scope", "casehubio/bulk")
        .when()
            .get("/preferences")
        .then()
            .statusCode(200)
            .body("size()", is(0));
    }

    @Test
    void set_upserts_existing_preference() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {"namespace": "test", "name": "upsert", "subKey": "", "value": "first"}
                """)
            .queryParam("scope", "casehubio/upsert")
        .when()
            .put("/preferences")
        .then()
            .statusCode(204);

        given()
            .contentType(ContentType.JSON)
            .body("""
                {"namespace": "test", "name": "upsert", "subKey": "", "value": "second"}
                """)
            .queryParam("scope", "casehubio/upsert")
        .when()
            .put("/preferences")
        .then()
            .statusCode(204);

        given()
            .queryParam("scope", "casehubio/upsert")
        .when()
            .get("/preferences")
        .then()
            .statusCode(200)
            .body("size()", is(1))
            .body("[0].value", is("second"));
    }

    @Test
    void list_without_scope_returns_all_records_across_scopes() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                      {"namespace": "bulk-all", "name": "root-pref", "subKey": "", "value": "r"}
                      """)
                .queryParam("scope", "")
                .when()
                .put("/preferences")
                .then()
                .statusCode(204);

        given()
                .contentType(ContentType.JSON)
                .body("""
                      {"namespace": "bulk-all", "name": "child-pref", "subKey": "", "value": "c"}
                      """)
                .queryParam("scope", "casehubio/alltest")
                .when()
                .put("/preferences")
                .then()
                .statusCode(204);

        given()
                .when()
                .get("/preferences")
                .then()
                .statusCode(200)
                .body("findAll { it.namespace == 'bulk-all' }.size()", greaterThanOrEqualTo(2));
    }

}
