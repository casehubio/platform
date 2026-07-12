package io.casehub.platform.notification.settings.jpa;

import io.casehub.platform.api.util.UUIDv7;
import io.casehub.platform.api.notification.settings.MuteRule;
import io.casehub.platform.api.notification.settings.MuteRuleInput;
import io.casehub.platform.api.notification.settings.MuteScope;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "mute_rules",
        indexes = {
                @Index(name = "idx_mute_rules_user_tenant", columnList = "user_id, tenancy_id")
        })
public class MuteRuleEntity extends PanacheEntityBase {

    @Id
    @Column(length = 36, nullable = false)
    public String id;

    @Column(name = "user_id", nullable = false)
    public String userId;

    @Column(name = "tenancy_id", nullable = false)
    public String tenancyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public MuteScope scope;

    @Column(name = "scope_id", nullable = false, length = 500)
    public String scopeId;

    @Column(name = "entity_type", length = 500)
    public String entityType;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "expires_at")
    public Instant expiresAt;

    /**
     * Build entity from input. Generates UUIDv7 id, captures createdAt.
     */
    static MuteRuleEntity fromInput(MuteRuleInput input) {
        if (input.scope() == MuteScope.ENTITY && input.entityType() == null) {
            throw new IllegalArgumentException("entityType is required for ENTITY scope");
        }

        MuteRuleEntity entity = new MuteRuleEntity();
        entity.id = UUIDv7.generate();
        entity.userId = input.userId();
        entity.tenancyId = input.tenancyId();
        entity.scope = input.scope();
        entity.scopeId = input.scopeId();
        entity.entityType = input.entityType();
        entity.createdAt = Instant.now();
        entity.expiresAt = input.expiresAt();
        return entity;
    }

    /**
     * Convert entity to domain record.
     */
    MuteRule toMuteRule() {
        return new MuteRule(
                id,
                userId,
                tenancyId,
                scope,
                scopeId,
                entityType,
                createdAt,
                expiresAt
        );
    }
}
