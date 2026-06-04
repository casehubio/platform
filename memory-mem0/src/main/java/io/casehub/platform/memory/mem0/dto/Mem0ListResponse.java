package io.casehub.platform.memory.mem0.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record Mem0ListResponse(
    @JsonProperty("results") List<Mem0Memory> results
) {}
