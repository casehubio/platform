package io.casehub.platform.registration.core;

import io.casehub.platform.api.authn.UserResolver;
import io.casehub.platform.api.identity.PrincipalId;
import io.casehub.platform.registration.UserStatus;
import io.casehub.platform.registration.UserStore;

import java.util.Objects;
import java.util.Optional;

public class UserStoreUserResolver implements UserResolver {

    private final UserStore userStore;

    public UserStoreUserResolver(UserStore userStore) {
        this.userStore = Objects.requireNonNull(userStore);
    }

    @Override
    public Optional<PrincipalId> resolveByEmail(String email, String tenancyId) {
        return userStore.findByEmail(email, tenancyId)
                .filter(user -> user.status() != UserStatus.REJECTED
                             && user.status() != UserStatus.SUSPENDED)
                .map(user -> PrincipalId.human(user.id()));
    }
}
