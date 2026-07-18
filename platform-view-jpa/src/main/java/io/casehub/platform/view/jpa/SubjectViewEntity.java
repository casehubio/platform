package io.casehub.platform.view.jpa;

import io.casehub.platform.api.path.Path;
import io.casehub.platform.api.view.SubjectViewSpec;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "subject_view")
public class SubjectViewEntity extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(nullable = false)
    public String name;

    @Column(name = "tenancy_id", nullable = false)
    public String tenancyId;

    @Column(name = "label_pattern", nullable = false, length = 500)
    public String labelPattern;

    @Column(length = 500)
    public String scope;

    @Column(name = "sort_field", length = 50)
    public String sortField;

    @Column(name = "sort_direction", length = 4)
    public String sortDirection;
    @Column(name = "additional_conditions", columnDefinition = "TEXT")
    public String additionalConditions;


    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }

    public SubjectViewSpec toSpec() {
        Path parsedScope = null;
        if (scope != null && !scope.isEmpty()) {
            parsedScope = Path.parse(scope);
        } else if (scope != null) {
            parsedScope = Path.root();
        }
        return new SubjectViewSpec(id, name, tenancyId, labelPattern,
                                   parsedScope, sortField, sortDirection,
                                   additionalConditions, createdAt);
    }

    public static SubjectViewEntity fromSpec(SubjectViewSpec spec) {
        var entity = new SubjectViewEntity();
        entity.id                   = spec.id();
        entity.name                 = spec.name();
        entity.tenancyId            = spec.tenancyId();
        entity.labelPattern         = spec.labelPattern();
        entity.scope                = spec.scope() != null ? spec.scope().value() : null;
        entity.sortField            = spec.sortField();
        entity.sortDirection        = spec.sortDirection();
        entity.additionalConditions = spec.additionalConditions();
        entity.createdAt            = spec.createdAt();
        return entity;
    }
}
