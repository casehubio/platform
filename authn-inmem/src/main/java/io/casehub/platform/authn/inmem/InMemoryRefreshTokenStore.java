package io.casehub.platform.authn.inmem;

import io.casehub.platform.api.authn.RefreshTokenRecord;
import io.casehub.platform.api.authn.RefreshTokenStore;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryRefreshTokenStore implements RefreshTokenStore {

    private final ConcurrentHashMap<String, RefreshTokenRecord> tokens = new ConcurrentHashMap<>();

    @Override
    public void store(RefreshTokenRecord record) {
        tokens.put(record.token(), record);
    }

    @Override
    public Optional<RefreshTokenRecord> findByToken(String token) {
        return Optional.ofNullable(tokens.get(token));
    }

    @Override
    public void consume(String token) {
        tokens.computeIfPresent(token, (k, r) -> new RefreshTokenRecord(
                r.token(), r.familyId(), r.actorId(), r.tenancyId(),
                r.groups(), r.authMethod(), r.createdAt(), r.expiresAt(), true));
    }

    @Override
    public void revokeFamily(String familyId) {
        tokens.entrySet().removeIf(e -> e.getValue().familyId().equals(familyId));
    }

    @Override
    public void revokeByActorId(String actorId, String tenancyId) {
        tokens.entrySet().removeIf(e ->
                e.getValue().actorId().equals(actorId)
                        && e.getValue().tenancyId().equals(tenancyId));
    }

    @Override
    public int purgeExpired(Instant before) {
        int[] count = {0};
        tokens.entrySet().removeIf(e -> {
            if (e.getValue().expiresAt() != null && e.getValue().expiresAt().isBefore(before)) {
                count[0]++;
                return true;
            }
            return false;
        });
        return count[0];
    }

    public int size() {
        return tokens.size();
    }
}
