package io.casehub.platform.registration.inmem;

import io.casehub.platform.registration.UserRecord;
import io.casehub.platform.registration.UserStatus;
import io.casehub.platform.registration.UserStore;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryUserStore implements UserStore {

    private final ConcurrentHashMap<String, UserRecord> store = new ConcurrentHashMap<>();

    @Override
    public UserRecord create(UserRecord user) {
        for (UserRecord existing : store.values()) {
            if (existing.email().equalsIgnoreCase(user.email())
                    && existing.tenancyId().equals(user.tenancyId())) {
                throw new IllegalArgumentException(
                        "User with email " + user.email() + " already exists in tenant " + user.tenancyId());
            }
        }
        store.put(user.id(), user);
        return user;
    }

    @Override
    public Optional<UserRecord> findById(String id, String tenancyId) {
        var user = store.get(id);
        if (user != null && user.tenancyId().equals(tenancyId)) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    @Override
    public Optional<UserRecord> findByEmail(String email, String tenancyId) {
        return store.values().stream()
                .filter(u -> u.email().equalsIgnoreCase(email) && u.tenancyId().equals(tenancyId))
                .findFirst();
    }

    @Override
    public UserRecord updateStatus(String id, String tenancyId, UserStatus newStatus) {
        var existing = findById(id, tenancyId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
        var activatedAt = newStatus == UserStatus.ACTIVE && existing.activatedAt() == null
                ? Instant.now()
                : existing.activatedAt();
        var updated = new UserRecord(
                existing.id(), existing.email(), existing.displayName(), existing.tenancyId(),
                newStatus, existing.registrationMethod(), existing.createdAt(), activatedAt);
        store.put(id, updated);
        return updated;
    }

    @Override
    public void delete(String id, String tenancyId) {
        var existing = store.get(id);
        if (existing != null && existing.tenancyId().equals(tenancyId)) {
            store.remove(id);
        }
    }
}
