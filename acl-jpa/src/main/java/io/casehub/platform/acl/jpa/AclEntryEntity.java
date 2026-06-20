package io.casehub.platform.acl.jpa;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "acl_entry",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_acl_entry",
                columnNames = {"actor_id", "resource_id", "action"}),
        indexes = {
                @Index(name = "idx_acl_actor_resource", columnList = "actor_id, resource_id"),
                @Index(name = "idx_acl_resource", columnList = "resource_id"),
                @Index(name = "idx_acl_tenancy", columnList = "tenancy_id")
        })
public class AclEntryEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "acl_entry_seq")
    @SequenceGenerator(name = "acl_entry_seq", sequenceName = "acl_entry_seq", allocationSize = 50)
    public Long id;

    @Column(name = "actor_id", nullable = false)
    public String actorId;

    @Column(name = "resource_id", nullable = false)
    public String resourceId;

    @Column(nullable = false)
    public String action;

    @Column(name = "condition")
    public String condition;

    @Column(name = "granted_at", nullable = false)
    public Instant grantedAt;

    @Column(name = "expires_at")
    public Instant expiresAt;

    @Column(name = "tenancy_id", nullable = false)
    public String tenancyId;
}
