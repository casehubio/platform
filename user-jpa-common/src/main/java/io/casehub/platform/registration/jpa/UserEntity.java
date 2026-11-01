package io.casehub.platform.registration.jpa;

import io.casehub.platform.registration.UserRecord;
import io.casehub.platform.registration.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "registration_user",
       uniqueConstraints = @UniqueConstraint(columnNames = {"email", "tenancy_id"}),
       indexes = @Index(name = "idx_reg_user_tenant", columnList = "tenancy_id"))
public class UserEntity {

    @Id
    @Column(name = "id")
    public String id;

    @Column(name = "email", nullable = false)
    public String email;

    @Column(name = "display_name")
    public String displayName;

    @Column(name = "tenancy_id", nullable = false)
    public String tenancyId;

    @Column(name = "status", nullable = false, length = 50)
    public String status;

    @Column(name = "registration_method", nullable = false, length = 50)
    public String registrationMethod;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "activated_at")
    public Instant activatedAt;

    public static UserEntity fromRecord(UserRecord record) {
        UserEntity e = new UserEntity();
        e.id = record.id();
        e.email = record.email();
        e.displayName = record.displayName();
        e.tenancyId = record.tenancyId();
        e.status = record.status().name();
        e.registrationMethod = record.registrationMethod();
        e.createdAt = record.createdAt();
        e.activatedAt = record.activatedAt();
        return e;
    }

    public UserRecord toRecord() {
        return new UserRecord(
                id, email, displayName, tenancyId,
                UserStatus.valueOf(status),
                registrationMethod, createdAt, activatedAt);
    }
}
