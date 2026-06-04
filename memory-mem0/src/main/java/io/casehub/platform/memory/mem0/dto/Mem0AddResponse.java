package io.casehub.platform.memory.mem0.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record Mem0AddResponse(
    @JsonProperty("results") List<Mem0MemoryResult> results
) {}
