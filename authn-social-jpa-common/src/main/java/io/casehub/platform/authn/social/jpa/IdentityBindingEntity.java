package io.casehub.platform.authn.social.jpa;

import io.casehub.platform.api.authn.IdentityBinding;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "authn_identity_binding",
       indexes = {
               @Index(name = "idx_authn_identity_binding_tenant_actor",
                      columnList = "tenancy_id, actor_id")
       })
@IdClass(IdentityBindingId.class)
public class IdentityBindingEntity {

    @Id
    @Column(nullable = false, length = 100)
    public String provider;

    @Id
    @Column(name = "external_id", nullable = false, length = 500)
    public String externalId;

    @Id
    @Column(name = "tenancy_id", nullable = false)
    public String tenancyId;

    @Column(name = "actor_id", nullable = false)
    public String actorId;

    @Column(length = 500)
    public String email;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    public static IdentityBindingEntity fromRecord(IdentityBinding binding) {
        IdentityBindingEntity entity = new IdentityBindingEntity();
        entity.provider   = binding.provider();
        entity.externalId = binding.externalId();
        entity.tenancyId  = binding.tenancyId();
        entity.actorId    = binding.actorId();
        entity.email      = binding.email();
        entity.createdAt  = binding.createdAt() != null ? binding.createdAt() : Instant.now();
        return entity;
    }

    public IdentityBinding toRecord() {
        return new IdentityBinding(provider, externalId, actorId, tenancyId, email, createdAt);
    }
}
