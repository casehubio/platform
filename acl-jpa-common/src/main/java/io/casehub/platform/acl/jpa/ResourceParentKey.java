package io.casehub.platform.acl.jpa;

import java.io.Serializable;

public record ResourceParentKey(String childResourceId, String tenancyId) implements Serializable {}