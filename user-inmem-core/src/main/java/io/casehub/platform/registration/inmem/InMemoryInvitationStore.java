package io.casehub.platform.registration.inmem;

import io.casehub.platform.registration.InvitationRecord;
import io.casehub.platform.registration.InvitationStore;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryInvitationStore implements InvitationStore {

    private final ConcurrentHashMap<String, InvitationRecord> store = new ConcurrentHashMap<>();

    @Override
    public InvitationRecord store(InvitationRecord invitation) {
        store.put(invitation.id(), invitation);
        return invitation;
    }

    @Override
    public Optional<InvitationRecord> findByTokenHash(String tokenHash) {
        return store.values().stream()
                .filter(r -> r.tokenHash().equals(tokenHash))
                .findFirst();
    }

    @Override
    public Optional<InvitationRecord> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<InvitationRecord> findByEmail(String email, String tenancyId) {
        return store.values().stream()
                .filter(r -> r.email().equalsIgnoreCase(email) && r.tenancyId().equals(tenancyId))
                .findFirst();
    }

    @Override
    public void consume(String tokenHash) {
        store.values().stream()
                .filter(r -> r.tokenHash().equals(tokenHash))
                .findFirst()
                .ifPresent(r -> store.put(r.id(), new InvitationRecord(
                        r.id(), r.tokenHash(), r.email(), r.tenancyId(),
                        r.assignedGroups(), r.expiresAt(), r.createdBy(),
                        r.createdAt(), true)));
    }

    @Override
    public List<InvitationRecord> findByTenancy(String tenancyId) {
        return store.values().stream()
                .filter(r -> r.tenancyId().equals(tenancyId))
                .toList();
    }

    @Override
    public void delete(String id) {
        store.remove(id);
    }
}
