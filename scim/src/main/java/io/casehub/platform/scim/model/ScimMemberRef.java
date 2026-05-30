package io.casehub.platform.scim.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ScimMemberRef(String value, String display) {}
