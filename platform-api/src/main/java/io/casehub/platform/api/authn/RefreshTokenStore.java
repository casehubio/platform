package io.casehub.platform.api.authn;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenStore {
    void store(RefreshTokenRecord record);
    Optional<RefreshTokenRecord> findByToken(String token);
    void consume(String token);
    void revokeFamily(String familyId);
    void revokeByActorId(String actorId, String tenancyId);
    int purgeExpired(Instant before);
}
