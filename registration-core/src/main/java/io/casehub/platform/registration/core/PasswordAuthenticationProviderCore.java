package io.casehub.platform.registration.core;

import io.casehub.platform.api.authn.AccountNotActiveException;
import io.casehub.platform.api.authn.AuthenticationContext;
import io.casehub.platform.api.authn.AuthenticationProvider;
import io.casehub.platform.api.authn.AuthenticationResult;
import io.casehub.platform.api.authn.ChallengeRecord;
import io.casehub.platform.api.authn.ChallengeResponse;
import io.casehub.platform.api.authn.InvalidCredentialException;
import io.casehub.platform.api.identity.PrincipalId;
import io.casehub.platform.registration.PasswordCredentialStore;
import io.casehub.platform.registration.PasswordHasher;
import io.casehub.platform.registration.UserStatus;
import io.casehub.platform.registration.UserStore;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class PasswordAuthenticationProviderCore implements AuthenticationProvider {

    private static final String METHOD = "password";

    private final PasswordCredentialStore credentialStore;
    private final UserStore userStore;
    private final PasswordHasher passwordHasher;

    public PasswordAuthenticationProviderCore(PasswordCredentialStore credentialStore,
                                              UserStore userStore,
                                              PasswordHasher passwordHasher) {
        this.credentialStore = Objects.requireNonNull(credentialStore);
        this.userStore = Objects.requireNonNull(userStore);
        this.passwordHasher = Objects.requireNonNull(passwordHasher);
    }

    @Override
    public String method() {
        return METHOD;
    }

    @Override
    public ChallengeResponse initiate(AuthenticationContext context) {
        return new PasswordChallengeResponse(
                UUID.randomUUID().toString(),
                Instant.now().plusSeconds(300));
    }

    @Override
    public AuthenticationResult verify(ChallengeRecord challenge, Map<String, Object> data) {
        String email = (String) data.get("email");
        String password = (String) data.get("password");
        String tenancyId = challenge.tenancyId();

        var userOpt = userStore.findByEmail(email, tenancyId);
        if (userOpt.isEmpty()) {
            passwordHasher.hash("timing-equalization-dummy");
            throw new InvalidCredentialException(METHOD, "Invalid credentials");
        }
        var user = userOpt.get();

        if (user.status() != UserStatus.ACTIVE) {
            throw new AccountNotActiveException(METHOD, user.status().name());
        }

        var credential = credentialStore.findByActorId(user.id(), tenancyId)
                .orElseThrow(() -> new InvalidCredentialException(METHOD, "Invalid credentials"));

        if (!passwordHasher.verify(password, credential.hashedPassword())) {
            throw new InvalidCredentialException(METHOD, "Invalid credentials");
        }

        credentialStore.updateLastUsed(user.id(), tenancyId);

        return new AuthenticationResult(
                PrincipalId.human(user.id()),
                tenancyId,
                Set.of(),
                METHOD,
                Map.of("userStatus", user.status().name()));
    }
}
