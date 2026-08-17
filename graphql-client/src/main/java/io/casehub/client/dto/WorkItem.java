package io.casehub.client.dto;

import java.time.Instant;
import java.util.UUID;

public record WorkItem(UUID id, String name, String status, String assigneeId,
                       Instant createdAt, Instant updatedAt) {
}
