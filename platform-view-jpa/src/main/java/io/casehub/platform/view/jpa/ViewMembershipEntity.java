package io.casehub.platform.view.jpa;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "view_membership")
@IdClass(ViewMembershipEntity.Key.class)
public class ViewMembershipEntity extends PanacheEntityBase {

    @Id
    @Column(name = "subject_id")
    public UUID subjectId;

    @Id
    @Column(name = "view_id")
    public UUID viewId;

    @Column(name = "view_name", nullable = false)
    public String viewName;

    public static class Key implements Serializable {
        public UUID subjectId;
        public UUID viewId;

        public Key() {}

        public Key(UUID subjectId, UUID viewId) {
            this.subjectId = subjectId;
            this.viewId = viewId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key k)) return false;
            return Objects.equals(subjectId, k.subjectId)
                && Objects.equals(viewId, k.viewId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(subjectId, viewId);
        }
    }
}
