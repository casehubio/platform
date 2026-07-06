package io.casehub.platform.notification.settings.jpa;

import io.casehub.platform.api.notification.settings.Snooze;
import io.casehub.platform.api.notification.settings.SnoozeInput;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "snooze")
@IdClass(SnoozeEntity.SnoozePK.class)
public class SnoozeEntity extends PanacheEntityBase {

    @Id
    @Column(name = "user_id", nullable = false)
    public String userId;

    @Id
    @Column(name = "tenancy_id", nullable = false)
    public String tenancyId;

    @Column(name = "until_time", nullable = false)
    public Instant until;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    /**
     * Build entity from input. Captures createdAt.
     */
    static SnoozeEntity fromInput(SnoozeInput input) {
        SnoozeEntity entity = new SnoozeEntity();
        entity.userId = input.userId();
        entity.tenancyId = input.tenancyId();
        entity.until = input.until();
        entity.createdAt = Instant.now();
        return entity;
    }

    /**
     * Convert entity to domain record.
     */
    Snooze toSnooze() {
        return new Snooze(
                userId,
                tenancyId,
                until,
                createdAt
        );
    }

    /**
     * Composite primary key for (userId, tenancyId).
     */
    public static class SnoozePK implements Serializable {
        public String userId;
        public String tenancyId;

        public SnoozePK() {}

        public SnoozePK(String userId, String tenancyId) {
            this.userId = userId;
            this.tenancyId = tenancyId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SnoozePK that = (SnoozePK) o;
            return Objects.equals(userId, that.userId) && Objects.equals(tenancyId, that.tenancyId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, tenancyId);
        }
    }
}
