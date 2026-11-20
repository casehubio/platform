package io.casehub.platform.authn;

import io.casehub.platform.api.authn.AuthenticationContext;
import io.casehub.platform.api.authn.AuthenticationResult;
import io.casehub.platform.api.authn.AuthenticationRouter;
import io.casehub.platform.api.authn.ChallengeResponse;
import io.casehub.platform.api.authn.WebAuthnCredential;
import io.casehub.platform.api.authn.WebAuthnCredentialStore;
import io.casehub.platform.api.identity.PrincipalId;
import io.casehub.platform.authn.quarkus.WebAuthnEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebAuthnEndpointTest {

    private WebAuthnEndpoint endpoint;
    private StubCredentialStore credentialStore;
    private RecordingAuthRouter authRouter;

    @BeforeEach
    void setUp() throws Exception {
        endpoint = new WebAuthnEndpoint();
        credentialStore = new StubCredentialStore();
        authRouter = new RecordingAuthRouter();

        setField(endpoint, "credentialStore", credentialStore);
        setField(endpoint, "authenticationRouter", authRouter);
    }

    @Test
    void listCredentialsDelegatesToStore() {
        credentialStore.credentials = List.of(
                new WebAuthnCredential("cred-1", "user-1", "tenant-1",
                        new byte[]{1, 2, 3}, 0, Set.of("usb"), null,
                        "My YubiKey", Instant.now(), null, true));

        var result = endpoint.listCredentials("user-1", "tenant-1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).credentialId()).isEqualTo("cred-1");
        assertThat(result.get(0).displayName()).isEqualTo("My YubiKey");
    }

    @Test
    void deleteCredentialDelegatesToStore() {
        credentialStore.credentials = List.of(
                new WebAuthnCredential("cred-1", "user-1", "tenant-1",
                                       new byte[]{1, 2, 3}, 0, Set.of("usb"), null,
                                       "My YubiKey", Instant.now(), null, true));

        endpoint.deleteCredential("cred-1", "user-1", "tenant-1");

        assertThat(credentialStore.deletedId).isEqualTo("cred-1");
    }

    @Test
    void deleteCredentialRejectsNonOwner() {
        credentialStore.credentials = List.of(
                new WebAuthnCredential("cred-1", "user-1", "tenant-1",
                                       new byte[]{1, 2, 3}, 0, Set.of("usb"), null,
                                       "My YubiKey", Instant.now(), null, true));

        assertThatThrownBy(() -> endpoint.deleteCredential("cred-1", "other-user", "tenant-1"))
                .isInstanceOf(SecurityException.class);
    }


    @Test
    void registerOptionsInitiatesWebAuthnFlow() {
        authRouter.challengeResponse = new SimpleChallengeResponse(
                "challenge-123", Instant.now().plusSeconds(300));

        var result = endpoint.registerOptions("https://example.com", "user-1", "tenant-1");

        assertThat(result.challengeId()).isEqualTo("challenge-123");
        assertThat(authRouter.lastContext.method()).isEqualTo("webauthn");
        assertThat(authRouter.lastContext.tenancyId()).isEqualTo("tenant-1");
        assertThat(authRouter.lastContext.origin()).isEqualTo("https://example.com");
        assertThat(authRouter.lastContext.existingPrincipal())
                .isPresent()
                .get().isEqualTo(PrincipalId.human("user-1"));
    }

    @Test
    void registerCompleteVerifiesAttestation() {
        authRouter.verifyResult = new AuthenticationResult(
                PrincipalId.human("user-1"), "tenant-1",
                Set.of(), "webauthn", Map.of());

        var attestation = Map.<String, Object>of("id", "cred-1", "response", Map.of());
        var result = endpoint.registerComplete("challenge-123", attestation, "tenant-1");

        assertThat(result.principal()).isEqualTo(PrincipalId.human("user-1"));
        assertThat(authRouter.lastVerifyMethod).isEqualTo("webauthn");
        assertThat(authRouter.lastVerifyChallengeId).isEqualTo("challenge-123");
        assertThat(authRouter.lastVerifyData).containsKey("id");
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private record SimpleChallengeResponse(String challengeId, Instant expiresAt) implements ChallengeResponse {}

    private static class StubCredentialStore implements WebAuthnCredentialStore {
        List<WebAuthnCredential> credentials = List.of();
        String deletedId;

        @Override
        public void store(WebAuthnCredential credential) {}

        @Override
        public Optional<WebAuthnCredential> findByCredentialId(String credentialId) {
            return credentials.stream()
                              .filter(c -> c.credentialId().equals(credentialId))
                              .findFirst();
        }

        @Override
        public List<WebAuthnCredential> findByActorId(String actorId, String tenancyId) {
            return credentials;
        }

        @Override
        public void updateAfterAuthentication(String credentialId, long newSignCount, Instant lastUsedAt) {}

        @Override
        public void delete(String credentialId) {
            deletedId = credentialId;
        }
    }

    private static class RecordingAuthRouter implements AuthenticationRouter {
        ChallengeResponse challengeResponse;
        AuthenticationResult verifyResult;
        AuthenticationContext lastContext;
        String lastVerifyMethod;
        String lastVerifyChallengeId;
        Map<String, Object> lastVerifyData;

        @Override
        public ChallengeResponse initiate(AuthenticationContext context) {
            lastContext = context;
            return challengeResponse;
        }

        @Override
        public AuthenticationResult verify(String method, String challengeId, Map<String, Object> data) {
            lastVerifyMethod = method;
            lastVerifyChallengeId = challengeId;
            lastVerifyData = data;
            return verifyResult;
        }

        @Override
        public Set<String> availableMethods() {
            return Set.of("webauthn");
        }
    }
}
