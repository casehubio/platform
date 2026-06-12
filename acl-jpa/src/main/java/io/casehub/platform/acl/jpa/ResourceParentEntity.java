package io.casehub.platform.acl.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "resource_parent")
public class ResourceParentEntity {

    @Id
    @Column(name = "child_resource_id")
    private String childResourceId;

    @Column(name = "parent_resource_id", nullable = false)
    private String parentResourceId;

    @Column(name = "tenancy_id", nullable = false)
    private String tenancyId;

    protected ResourceParentEntity() {}

    public ResourceParentEntity(String childResourceId, String parentResourceId, String tenancyId) {
        this.childResourceId = childResourceId;
        this.parentResourceId = parentResourceId;
        this.tenancyId = tenancyId;
    }

    public String getChildResourceId() { return childResourceId; }
    public String getParentResourceId() { return parentResourceId; }
    public String getTenancyId() { return tenancyId; }
}
