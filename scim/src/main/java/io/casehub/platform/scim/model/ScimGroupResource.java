package io.casehub.platform.scim.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ScimGroupResource(String id, String displayName, List<ScimMemberRef> members) {}
