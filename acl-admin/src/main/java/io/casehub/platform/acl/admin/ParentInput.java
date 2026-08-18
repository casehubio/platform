package io.casehub.platform.acl.admin;

public record ParentInput(io.casehub.platform.api.acl.ResourceId childResourceId,
                          io.casehub.platform.api.acl.ResourceId parentResourceId) {
}
