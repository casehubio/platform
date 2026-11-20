package io.casehub.platform.authn;

import io.casehub.platform.api.authn.AuthenticationContext;
import io.casehub.platform.api.authn.AuthenticationResult;
import io.casehub.platform.api.authn.AuthenticationRouter;
import io.casehub.platform.api.authn.ChallengeResponse;
import io.casehub.platform.api.identity.PrincipalId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SocialAuthEndpointCoreTest {

    private SocialAuthEndpointCore endpoint;
    private RecordingAuthRouter authRouter;

    @BeforeEach
    void setUp() {
        authRouter = new RecordingAuthRouter();
        endpoint = new SocialAuthEndpointCore(authRouter);
    }

    @Test
    void authorizeInitiatesOAuthFlow() {
        authRouter.challengeResponse = new SimpleChallengeResponse(
                "challenge-456", Instant.now().plusSeconds(600));

        var result = endpoint.authorize("google", "https://app.example.com", "tenant-1");

        assertThat(result.challengeId()).isEqualTo("challenge-456");
        assertThat(authRouter.lastContext.method()).isEqualTo("google");
        assertThat(authRouter.lastContext.tenancyId()).isEqualTo("tenant-1");
        assertThat(authRouter.lastContext.origin()).isEqualTo("https://app.example.com");
        assertThat(authRouter.lastContext.existingPrincipal()).isEmpty();
    }

    @Test
    void callbackVerifiesOAuthCode() {
        authRouter.verifyResult = new AuthenticationResult(
                PrincipalId.human("user-1"), "tenant-1",
                Set.of("users"), "google", Map.of());

        var result = endpoint.callback("google", "auth-code-xyz", "state-abc", "tenant-1");

        assertThat(result.principal()).isEqualTo(PrincipalId.human("user-1"));
        assertThat(result.method()).isEqualTo("google");
        assertThat(authRouter.lastVerifyMethod).isEqualTo("google");
        assertThat(authRouter.lastVerifyChallengeId).isEqualTo("state-abc");
        assertThat(authRouter.lastVerifyData).containsEntry("code", "auth-code-xyz");
    }

    @Test
    void authorizeWithDifferentProviders() {
        authRouter.challengeResponse = new SimpleChallengeResponse(
                "gh-challenge", Instant.now().plusSeconds(600));

        endpoint.authorize("github", "https://app.example.com", "tenant-1");
        assertThat(authRouter.lastContext.method()).isEqualTo("github");

        endpoint.authorize("apple", "https://app.example.com", "tenant-1");
        assertThat(authRouter.lastContext.method()).isEqualTo("apple");
    }

    private record SimpleChallengeResponse(String challengeId, Instant expiresAt) implements ChallengeResponse {}

    private static class RecordingAuthRouter implements AuthenticationRouter {
        ChallengeResponse challengeResponse;
        AuthenticationResult verifyResult;
        AuthenticationContext lastContext;
        String lastVerifyMethod;
        String lastVerifyChallengeId;
        Map<String, Object> lastVerifyData;

        @Override public ChallengeResponse initiate(AuthenticationContext context) { lastContext = context; return challengeResponse; }
        @Override public AuthenticationResult verify(String method, String challengeId, Map<String, Object> data) {
            lastVerifyMethod = method; lastVerifyChallengeId = challengeId; lastVerifyData = data; return verifyResult;
        }
        @Override public Set<String> availableMethods() { return Set.of("google", "github", "apple"); }
    }
}
