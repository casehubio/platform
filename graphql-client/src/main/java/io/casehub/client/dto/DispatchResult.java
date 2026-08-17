package io.casehub.client.dto;

import java.util.UUID;

public record DispatchResult(UUID messageId, UUID ledgerEntryId) {
}
