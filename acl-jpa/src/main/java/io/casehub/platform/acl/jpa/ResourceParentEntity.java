package io.casehub.platform.acl.jpa;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "resource_parent",
        indexes = @Index(name = "idx_rp_parent", columnList = "parent_resource_id"))
public class ResourceParentEntity extends PanacheEntityBase {

    @Id
    @Column(name = "child_resource_id")
    public String childResourceId;

    @Column(name = "parent_resource_id", nullable = false)
    public String parentResourceId;

    @Column(name = "tenancy_id", nullable = false)
    public String tenancyId;
}
