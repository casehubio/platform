package io.casehub.platform.registration.core;

import io.casehub.platform.api.authn.UserStatusResolver;
import io.casehub.platform.registration.UserStore;

import java.util.Objects;
import java.util.Optional;

public class UserStoreUserStatusResolver implements UserStatusResolver {

    private final UserStore userStore;

    public UserStoreUserStatusResolver(UserStore userStore) {
        this.userStore = Objects.requireNonNull(userStore);
    }

    @Override
    public Optional<String> resolveStatus(String actorId, String tenancyId) {
        return userStore.findById(actorId, tenancyId)
                .map(user -> user.status().name());
    }
}
