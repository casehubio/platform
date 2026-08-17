package io.casehub.client.dto;

import java.time.Instant;
import java.util.UUID;

public record CaseInstance(UUID id, String namespace, String name, String status,
                           Instant createdAt, Instant updatedAt) {
}
