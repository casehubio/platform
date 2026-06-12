package io.casehub.platform.acl.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "acl_entry")
public class AclEntryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_id", nullable = false)
    private String actorId;

    @Column(name = "resource_id", nullable = false)
    private String resourceId;

    @Column(nullable = false)
    private String action;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "tenancy_id", nullable = false)
    private String tenancyId;

    protected AclEntryEntity() {}

    public AclEntryEntity(String actorId, String resourceId, String action,
                          Instant grantedAt, Instant expiresAt, String tenancyId) {
        this.actorId = actorId;
        this.resourceId = resourceId;
        this.action = action;
        this.grantedAt = grantedAt;
        this.expiresAt = expiresAt;
        this.tenancyId = tenancyId;
    }

    public Long getId() { return id; }
    public String getActorId() { return actorId; }
    public String getResourceId() { return resourceId; }
    public String getAction() { return action; }
    public Instant getGrantedAt() { return grantedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public String getTenancyId() { return tenancyId; }

    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public void setGrantedAt(Instant grantedAt) { this.grantedAt = grantedAt; }
}
