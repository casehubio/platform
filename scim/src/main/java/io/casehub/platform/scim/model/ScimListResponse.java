package io.casehub.platform.scim.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ScimListResponse<T>(
    int totalResults,
    int startIndex,
    int itemsPerPage,
    @JsonProperty("Resources") List<T> resources
) {}
