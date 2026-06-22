package io.casehub.platform.acl.jpa;

import com.fasterxml.jackson.databind.JsonNode;
import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "acl_audit_log",
        indexes = {
                @Index(name = "idx_audit_resource", columnList = "resource_id"),
                @Index(name = "idx_audit_actor", columnList = "actor_id"),
                @Index(name = "idx_audit_performed", columnList = "performed_by"),
                @Index(name = "idx_audit_tenancy", columnList = "tenancy_id")
        })
public class AclAuditLogEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "acl_audit_log_seq")
    @SequenceGenerator(name = "acl_audit_log_seq", sequenceName = "acl_audit_log_seq", allocationSize = 50)
    public Long id;

    @Column(name = "actor_id", nullable = false)
    public String actorId;

    @Column(name = "resource_id", nullable = false)
    public String resourceId;

    @Column(nullable = false, length = 50)
    public String action;

    @Column(nullable = false, length = 20)
    public String operation;

    @Column(name = "performed_by", nullable = false)
    public String performedBy;

    @Column(name = "performed_at", nullable = false)
    public Instant performedAt;

    @Column(name = "expires_at")
    public Instant expiresAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    public JsonNode metadata;

    @Column(name = "tenancy_id", nullable = false, length = 64)
    public String tenancyId;
}
