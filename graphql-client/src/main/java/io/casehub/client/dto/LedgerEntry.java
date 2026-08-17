package io.casehub.client.dto;

import io.casehub.platform.graphql.scalar.Json;

import java.time.Instant;
import java.util.UUID;

public record LedgerEntry(UUID id, UUID subjectId, String entryType, String actorId,
                          Instant occurredAt, Json domainData) {
}
