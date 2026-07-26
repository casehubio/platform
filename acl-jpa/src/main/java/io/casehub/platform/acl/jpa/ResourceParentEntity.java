package io.casehub.platform.acl.jpa;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@IdClass(ResourceParentKey.class)
@Entity
@Table(name = "resource_parent",
       indexes = @Index(name = "idx_rp_parent", columnList = "parent_resource_id"))
public class ResourceParentEntity extends PanacheEntityBase {

    @Id
    @Column(name = "child_resource_id")
    public String childResourceId;

    @Id
    @Column(name = "tenancy_id", nullable = false)
    public String tenancyId;

    @Column(name = "parent_resource_id", nullable = false)
    public String parentResourceId;
}
