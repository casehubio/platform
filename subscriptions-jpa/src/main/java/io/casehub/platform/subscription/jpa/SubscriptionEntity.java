package io.casehub.platform.subscription.jpa;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.platform.api.notification.UUIDv7;
import io.casehub.platform.api.subscription.Constraint;
import io.casehub.platform.api.subscription.NotificationTemplate;
import io.casehub.platform.api.subscription.Subscription;
import io.casehub.platform.api.subscription.SubscriptionInput;
import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "subscription",
        indexes = {
                @Index(name = "idx_subscription_user_tenant_enabled",
                        columnList = "user_id, tenancy_id, enabled, created_at DESC"),
                @Index(name = "idx_subscription_enabled",
                        columnList = "enabled")
        })
public class SubscriptionEntity extends PanacheEntityBase {

    private static final TypeReference<List<Constraint>> CONSTRAINT_LIST_TYPE =
            new TypeReference<>() {};

    @Id
    public String id;

    @Column(name = "user_id", nullable = false)
    public String userId;

    @Column(name = "tenancy_id", nullable = false)
    public String tenancyId;

    @Column(nullable = false, length = 500)
    public String name;

    @Column(name = "event_type", nullable = false, length = 500)
    public String eventType;

    @Column(name = "constraints_json", columnDefinition = "TEXT")
    public String constraintsJson;

    @Column(name = "template_json", nullable = false, columnDefinition = "TEXT")
    public String templateJson;

    @Column(nullable = false)
    public boolean enabled;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    /**
     * Build entity from input. Generates UUID v7 id, captures createdAt and updatedAt.
     * ObjectMapper passed in because entities cannot use CDI injection.
     */
    static SubscriptionEntity fromInput(SubscriptionInput input, ObjectMapper mapper) {
        SubscriptionEntity entity = new SubscriptionEntity();
        entity.id = UUIDv7.generate();
        entity.userId = input.userId();
        entity.tenancyId = input.tenancyId();
        entity.name = input.name();
        entity.eventType = input.eventType();
        entity.constraintsJson = serializeConstraints(input.constraints(), mapper);
        entity.templateJson = serializeTemplate(input.template(), mapper);
        entity.enabled = input.enabled();
        Instant now = Instant.now();
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    /**
     * Convert entity to domain record.
     */
    Subscription toSubscription(ObjectMapper mapper) {
        return new Subscription(
                id,
                userId,
                tenancyId,
                name,
                eventType,
                deserializeConstraints(constraintsJson, mapper),
                deserializeTemplate(templateJson, mapper),
                enabled,
                createdAt,
                updatedAt
        );
    }

    // JSON serialization helpers

    static String serializeConstraints(List<Constraint> constraints, ObjectMapper mapper) {
        if (constraints == null || constraints.isEmpty()) {
            return null;
        }
        try {
            return mapper.writeValueAsString(constraints);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize constraints", e);
        }
    }

    static List<Constraint> deserializeConstraints(String json, ObjectMapper mapper) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return List.copyOf(mapper.readValue(json, CONSTRAINT_LIST_TYPE));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize constraints", e);
        }
    }

    static String serializeTemplate(NotificationTemplate template, ObjectMapper mapper) {
        try {
            return mapper.writeValueAsString(template);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize template", e);
        }
    }

    static NotificationTemplate deserializeTemplate(String json, ObjectMapper mapper) {
        try {
            return mapper.readValue(json, NotificationTemplate.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize template", e);
        }
    }
}
