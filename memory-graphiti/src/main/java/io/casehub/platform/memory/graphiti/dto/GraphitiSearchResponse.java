package io.casehub.platform.memory.graphiti.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record GraphitiSearchResponse(
        @JsonProperty("facts") List<FactResult> facts
) {}
