package io.casehub.client.dto;

import java.time.Instant;
import java.util.UUID;

public record Channel(UUID id, String name, String semantics, String status,
                      Instant createdAt) {
}
