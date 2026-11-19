package io.casehub.platform.authn.inmem;

import io.casehub.platform.api.authn.CredentialState;
import io.casehub.platform.api.authn.CredentialType;
import io.casehub.platform.api.authn.StaticCredentialRecord;
import io.casehub.platform.api.authn.StaticCredentialStore;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryStaticCredentialStore implements StaticCredentialStore {

    private final ConcurrentHashMap<String, StaticCredentialRecord> store =
            new ConcurrentHashMap<>();
    private final Duration gracePeriodTtl;

    public InMemoryStaticCredentialStore() {
        this(Duration.ofHours(24));
    }

    public InMemoryStaticCredentialStore(Duration gracePeriodTtl) {
        this.gracePeriodTtl = gracePeriodTtl;
    }

    @Override
    public void store(StaticCredentialRecord record) {
        store.values().removeIf(r ->
                r.actorId().equals(record.actorId())
                && r.provider().equals(record.provider())
                && r.tenancyId().equals(record.tenancyId())
                && r.state() == CredentialState.ACTIVE);
        store.put(credKey(record.credential()), record);
    }

    @Override
    public Optional<StaticCredentialRecord> find(String actorId, String provider, String tenancyId) {
        return store.values().stream()
                .filter(r -> r.actorId().equals(actorId)
                        && r.provider().equals(provider)
                        && r.tenancyId().equals(tenancyId)
                        && r.state() == CredentialState.ACTIVE)
                .findFirst();
    }

    @Override
    public List<StaticCredentialRecord> findAll(String actorId, String tenancyId) {
        return store.values().stream()
                .filter(r -> r.actorId().equals(actorId)
                        && r.tenancyId().equals(tenancyId)
                        && r.state() == CredentialState.ACTIVE)
                .toList();
    }

    @Override
    public void delete(String actorId, String provider, String tenancyId) {
        store.values().removeIf(r ->
                r.actorId().equals(actorId)
                && r.provider().equals(provider)
                && r.tenancyId().equals(tenancyId));
    }

    @Override
    public Optional<StaticCredentialRecord> lookup(String credential) {
        byte[] inputBytes = credential.getBytes(StandardCharsets.UTF_8);
        return store.values().stream()
                .filter(r -> r.state() != CredentialState.REVOKED)
                .filter(r -> !r.isExpired())
                .filter(r -> r.credential() != null && MessageDigest.isEqual(
                        inputBytes,
                        r.credential().getBytes(StandardCharsets.UTF_8)))
                .findFirst();
    }

    @Override
    public StaticCredentialRecord rotate(String actorId, String provider,
            String tenancyId, String newCredential) {
        store.values().stream()
                .filter(r -> r.actorId().equals(actorId)
                        && r.provider().equals(provider)
                        && r.tenancyId().equals(tenancyId)
                        && r.state() == CredentialState.GRACE_PERIOD)
                .findFirst()
                .ifPresent(r -> store.put(credKey(r.credential()),
                        new StaticCredentialRecord(r.actorId(), r.tenancyId(), r.provider(),
                                r.credential(), r.type(), r.createdAt(), Instant.now(),
                                CredentialState.REVOKED, r.expiresAt())));

        var active = find(actorId, provider, tenancyId);
        CredentialType activeType = active.map(StaticCredentialRecord::type)
                .orElse(CredentialType.API_KEY);

        if (active.isPresent()) {
            var old = active.get();
            var graced = new StaticCredentialRecord(old.actorId(), old.tenancyId(), old.provider(),
                    old.credential(), old.type(), old.createdAt(), Instant.now(),
                    CredentialState.GRACE_PERIOD, Instant.now().plus(gracePeriodTtl));
            store.put(credKey(old.credential()), graced);
        }

        var now = Instant.now();
        var newRecord = new StaticCredentialRecord(actorId, tenancyId, provider,
                newCredential, activeType, now, now, CredentialState.ACTIVE, null);
        store.put(credKey(newCredential), newRecord);
        return newRecord;
    }

    @Override
    public void revoke(String actorId, String provider, String tenancyId) {
        store.replaceAll((key, r) -> {
            if (r.actorId().equals(actorId)
                    && r.provider().equals(provider)
                    && r.tenancyId().equals(tenancyId)
                    && r.state() != CredentialState.REVOKED) {
                return new StaticCredentialRecord(r.actorId(), r.tenancyId(), r.provider(),
                        r.credential(), r.type(), r.createdAt(), Instant.now(),
                        CredentialState.REVOKED, r.expiresAt());
            }
            return r;
        });
    }

    private static String credKey(String credential) {
        return "cred:" + credential;
    }
}
