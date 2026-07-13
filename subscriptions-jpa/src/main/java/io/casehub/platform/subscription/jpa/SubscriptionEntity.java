package io.casehub.platform.subscription.jpa;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.platform.api.subscription.Constraint;
import io.casehub.platform.api.subscription.NotificationTarget;
import io.casehub.platform.api.subscription.NotificationTemplate;
import io.casehub.platform.api.subscription.Subscription;
import io.casehub.platform.api.subscription.SubscriptionInput;
import io.casehub.platform.api.subscription.SubscriptionScope;
import io.casehub.platform.api.util.UUIDv7;
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
               @Index(name = "idx_subscription_owner_tenant_enabled",
                      columnList = "owner_id, tenancy_id, enabled, created_at DESC"),
               @Index(name = "idx_subscription_enabled",
                      columnList = "enabled")
       })
public class SubscriptionEntity extends PanacheEntityBase {

    private static final TypeReference<List<Constraint>>         CONSTRAINT_LIST_TYPE =
            new TypeReference<>() {};
    private static final TypeReference<List<NotificationTarget>> TARGET_LIST_TYPE     =
            new TypeReference<>() {};

    @Id
    public String id;

    @Column(name = "owner_id", nullable = false)
    public String ownerId;

    @Column(name = "tenancy_id", nullable = false)
    public String tenancyId;

    @Column(nullable = false, length = 500)
    public String name;

    @Column(name = "event_type", nullable = false, length = 500)
    public String eventType;

    @Column(name = "constraints_json", columnDefinition = "TEXT")
    public String constraintsJson;

    @Column(name = "targets_json", nullable = false, columnDefinition = "TEXT")
    public String targetsJson;

    @Column(name = "include_actor", nullable = false)
    public boolean includeActor;

    @Column(name = "template_json", nullable = false, columnDefinition = "TEXT")
    public String templateJson;

    @Column(nullable = false)
    public boolean enabled;
    @Column(nullable = false, length = 10)
    public String  scope;


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
        entity.id              = UUIDv7.generate();
        entity.ownerId         = input.ownerId();
        entity.tenancyId       = input.tenancyId();
        entity.name            = input.name();
        entity.eventType       = input.eventType();
        entity.constraintsJson = serializeConstraints(input.constraints(), mapper);
        entity.targetsJson     = serializeTargets(input.targets(), mapper);
        entity.includeActor    = input.includeActor();
        entity.templateJson    = serializeTemplate(input.template(), mapper);
        entity.enabled         = input.enabled();
        entity.scope           = input.scope().name();
        Instant now = Instant.now();
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

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

    // JSON serialization helpers

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

    static String serializeTargets(List<NotificationTarget> targets, ObjectMapper mapper) {
        try {
            return mapper.writeValueAsString(targets);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize targets", e);
        }
    }

    static List<NotificationTarget> deserializeTargets(String json, ObjectMapper mapper) {
        try {
            return List.copyOf(mapper.readValue(json, TARGET_LIST_TYPE));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize targets", e);
        }
    }

    /**
     * Convert entity to domain record.
     */
    Subscription toSubscription(ObjectMapper mapper) {
        return new Subscription(
                id,
                ownerId,
                tenancyId,
                name,
                eventType,
                deserializeConstraints(constraintsJson, mapper),
                deserializeTargets(targetsJson, mapper),
                includeActor,
                deserializeTemplate(templateJson, mapper),
                enabled,
                SubscriptionScope.valueOf(scope),
                createdAt,
                updatedAt
        );
    }
}
