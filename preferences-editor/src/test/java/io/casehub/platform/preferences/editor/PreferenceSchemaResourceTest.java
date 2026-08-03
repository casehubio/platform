package io.casehub.platform.preferences.editor;

import io.casehub.platform.api.preferences.BooleanPreference;
import io.casehub.platform.api.preferences.EnumOption;
import io.casehub.platform.api.preferences.IntPreference;
import io.casehub.platform.api.preferences.PreferenceConstraintKeys;
import io.casehub.platform.api.preferences.PreferenceKey;
import io.casehub.platform.api.preferences.PreferenceSchemaDescriptor;
import io.casehub.platform.api.preferences.PreferenceSchemaRegistry;
import io.casehub.platform.api.preferences.SingleValuePreference;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class PreferenceSchemaResourceTest {

    @Test
    void get_schema_returns_all_entries() {
        given()
                .when()
                .get("/preferences/schema")
                .then()
                .statusCode(200)
                .body("size()", greaterThanOrEqualTo(3));
    }

    @Test
    void get_schema_sorted_by_qualifiedName() {
        var names = given()
                            .when()
                            .get("/preferences/schema")
                            .then()
                            .statusCode(200)
                            .extract().jsonPath().getList("qualifiedName", String.class);

        var sorted = names.stream().sorted().toList();
        assertEquals(sorted, names);
    }

    @Test
    void get_schema_entry_has_correct_shape() {
        given()
                .when()
                .get("/preferences/schema")
                .then()
                .statusCode(200)
                .body("find { it.qualifiedName == 'casehub.work.sla.default-hours' }.namespace", is("casehub.work"))
                .body("find { it.qualifiedName == 'casehub.work.sla.default-hours' }.name", is("sla.default-hours"))
                .body("find { it.qualifiedName == 'casehub.work.sla.default-hours' }.type", is("integer"))
                .body("find { it.qualifiedName == 'casehub.work.sla.default-hours' }.label", is("Default SLA hours"))
                .body("find { it.qualifiedName == 'casehub.work.sla.default-hours' }.description", is("Hours before escalation"))
                .body("find { it.qualifiedName == 'casehub.work.sla.default-hours' }.defaultValue", is("24"))
                .body("find { it.qualifiedName == 'casehub.work.sla.default-hours' }.multiValue", is(false))
                .body("find { it.qualifiedName == 'casehub.work.sla.default-hours' }.constraints.min", is(1))
                .body("find { it.qualifiedName == 'casehub.work.sla.default-hours' }.constraints.max", is(720));
    }

    @Test
    void get_schema_enum_entry_has_options() {
        given()
                .when()
                .get("/preferences/schema")
                .then()
                .statusCode(200)
                .body("find { it.qualifiedName == 'casehub.work.delegation.decline-target' }.type", is("enum"))
                .body("find { it.qualifiedName == 'casehub.work.delegation.decline-target' }.options.size()", is(2))
                .body("find { it.qualifiedName == 'casehub.work.delegation.decline-target' }.options[0].value", is("POOL"))
                .body("find { it.qualifiedName == 'casehub.work.delegation.decline-target' }.options[0].label", is("Return to pool"));
    }

    @Test
    void get_schema_boolean_entry() {
        given()
                .when()
                .get("/preferences/schema")
                .then()
                .statusCode(200)
                .body("find { it.qualifiedName == 'casehub.platform.debug.enabled' }.type", is("boolean"))
                .body("find { it.qualifiedName == 'casehub.platform.debug.enabled' }.defaultValue", is("false"));
    }

    @Test
    void get_schema_filtered_by_namespace() {
        given()
                .queryParam("namespace", "casehub.work")
                .when()
                .get("/preferences/schema")
                .then()
                .statusCode(200)
                .body("size()", is(2))
                .body("every { it.namespace == 'casehub.work' }", is(true));
    }

    @Test
    void get_schema_nonexistent_namespace_returns_empty() {
        given()
                .queryParam("namespace", "nonexistent")
                .when()
                .get("/preferences/schema")
                .then()
                .statusCode(200)
                .body("size()", is(0));
    }

    @Test
    void get_schema_null_description_serializes_as_null() {
        given()
                .when()
                .get("/preferences/schema")
                .then()
                .statusCode(200)
                .body("find { it.qualifiedName == 'casehub.platform.debug.enabled' }.description", nullValue());
    }

    @Test
    void get_schema_returns_etag_header() {
        given()
                .when()
                .get("/preferences/schema")
                .then()
                .statusCode(200)
                .header("ETag", notNullValue());
    }

    @Test
    void get_schema_with_matching_if_none_match_returns_304() {
        String etag = given()
                              .when()
                              .get("/preferences/schema")
                              .then()
                              .statusCode(200)
                              .extract().header("ETag");

        given()
                .header("If-None-Match", etag)
                .when()
                .get("/preferences/schema")
                .then()
                .statusCode(304);
    }

    @Test
    void get_schema_with_stale_if_none_match_returns_200() {
        given()
                .header("If-None-Match", "\"stale-value\"")
                .when()
                .get("/preferences/schema")
                .then()
                .statusCode(200)
                .header("ETag", notNullValue())
                .body("size()", greaterThanOrEqualTo(3));
    }

    @Test
    void get_schema_without_if_none_match_returns_200_with_etag() {
        given()
                .when()
                .get("/preferences/schema")
                .then()
                .statusCode(200)
                .header("ETag", notNullValue())
                .body("size()", greaterThanOrEqualTo(3));
    }

    @Test
    void etag_is_same_regardless_of_namespace_filter() {
        String etagAll = given()
                                 .when()
                                 .get("/preferences/schema")
                                 .then()
                                 .statusCode(200)
                                 .extract().header("ETag");

        String etagFiltered = given()
                                      .queryParam("namespace", "casehub.work")
                                      .when()
                                      .get("/preferences/schema")
                                      .then()
                                      .statusCode(200)
                                      .extract().header("ETag");

        assertEquals(etagAll, etagFiltered);
    }

    @ApplicationScoped
    static class TestSchemaRegistrar {
        @Inject
        PreferenceSchemaRegistry registry;

        void onStart(@Observes StartupEvent event) {
            registry.register(PreferenceSchemaDescriptor.of(
                                                                new PreferenceKey<>("casehub.work", "sla.default-hours",
                                                                                    IntPreference.of(24), IntPreference::parse))
                                                        .label("Default SLA hours")
                                                        .description("Hours before escalation")
                                                        .constraints(Map.of(
                                                                PreferenceConstraintKeys.MIN, 1,
                                                                PreferenceConstraintKeys.MAX, 720))
                                                        .build());

            registry.register(PreferenceSchemaDescriptor.of(
                                                                new PreferenceKey<>("casehub.platform", "debug.enabled",
                                                                                    BooleanPreference.of(false), BooleanPreference::parse))
                                                        .label("Debug mode")
                                                        .build());

            record DeclineTarget(String value) implements SingleValuePreference {
                @Override
                public String toSerializedValue() {return value;}
            }
            registry.register(PreferenceSchemaDescriptor.of(
                                                                new PreferenceKey<>("casehub.work", "delegation.decline-target",
                                                                                    new DeclineTarget("POOL"), DeclineTarget::new))
                                                        .type("enum")
                                                        .label("Decline target")
                                                        .options(List.of(
                                                                new EnumOption("POOL", "Return to pool"),
                                                                new EnumOption("DELEGATOR", "Return to delegator")))
                                                        .build());
        }
    }
}
