package io.casehub.platform.subscription.rest;

import io.casehub.platform.api.subscription.EventFieldDescriptor;
import io.casehub.platform.api.subscription.EventTypeDescriptor;
import io.casehub.platform.api.subscription.EventTypeRegistry;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EventTypeResourceTest {

    @Inject
    EventTypeRegistry registry;

    @Test
    @Order(1)
    void listEventTypes_returnsEmptyWhenNoneRegistered() {
        given()
            .when().get("/subscriptions/event-types")
            .then()
            .statusCode(200)
            .body("$", hasSize(0));
    }

    @Test
    @Order(2)
    void listEventTypes_returnsRegisteredDescriptors() {
        registry.register(new EventTypeDescriptor(
                "io.casehub.work.workitem.completed",
                "Work Item Completed",
                "Fired on completion",
                List.of(new EventFieldDescriptor("assigneeId", "Assignee", "string"))));

        given()
            .when().get("/subscriptions/event-types")
            .then()
            .statusCode(200)
            .body("$", hasSize(1))
            .body("[0].eventType", equalTo("io.casehub.work.workitem.completed"))
            .body("[0].displayName", equalTo("Work Item Completed"))
            .body("[0].fields", hasSize(1))
            .body("[0].fields[0].name", equalTo("assigneeId"));
    }
}
