package io.casehub.platform.authn;

import io.casehub.platform.api.authn.ConsentRequest;
import io.casehub.platform.api.authn.IncrementalConsentHandler;
import io.casehub.platform.api.authn.ScopeRegistry;
import io.casehub.platform.api.authn.ServiceAccessToken;
import io.casehub.platform.api.authn.ServiceConnection;
import io.casehub.platform.api.authn.ServiceConnectionProvider;
import io.casehub.platform.api.authn.ServiceConnectionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectionEndpointCoreTest {

    private ConnectionEndpointCore endpoint;
    private StubConnectionProvider connectionProvider;
    private StubConsentHandler consentHandler;
    private StubScopeRegistry scopeRegistry;

    @BeforeEach
    void setUp() {
        connectionProvider = new StubConnectionProvider();
        consentHandler = new StubConsentHandler();
        scopeRegistry = new StubScopeRegistry();
        endpoint = new ConnectionEndpointCore(connectionProvider, consentHandler, scopeRegistry);
    }

    @Test
    void listConnectionsDelegatesToProvider() {
        connectionProvider.connections = List.of(
                new ServiceConnection("user-1", "google", "tenant-1",
                        ServiceConnectionStatus.CONNECTED,
                        Set.of("email", "calendar"), Set.of(), Instant.now()));

        var result = endpoint.listConnections("user-1", "tenant-1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).provider()).isEqualTo("google");
    }

    @Test
    void getConnectionDelegatesToProvider() {
        connectionProvider.singleConnection = new ServiceConnection(
                "user-1", "github", "tenant-1",
                ServiceConnectionStatus.PARTIAL,
                Set.of("repo"), Set.of("admin:org"), Instant.now());

        var result = endpoint.getConnection("github", "user-1", "tenant-1");

        assertThat(result.provider()).isEqualTo("github");
        assertThat(result.missingScopes()).containsExactly("admin:org");
    }

    @Test
    void requestConsentDelegatesToHandler() {
        consentHandler.response = new ConsentRequest(
                "https://accounts.google.com/o/oauth2/auth?scope=calendar",
                "google", Set.of("calendar"), "state-123");

        var result = endpoint.requestConsent("google", Set.of("calendar"), "user-1", "tenant-1");

        assertThat(result.provider()).isEqualTo("google");
        assertThat(result.requestedScopes()).containsExactly("calendar");
    }

    @Test
    void disconnectDelegatesToProvider() {
        endpoint.disconnect("github", "user-1", "tenant-1");

        assertThat(connectionProvider.disconnectedProvider).isEqualTo("github");
        assertThat(connectionProvider.disconnectedActor).isEqualTo("user-1");
    }

    @Test
    void listProvidersDelegatesToScopeRegistry() {
        scopeRegistry.providers = Set.of("google", "github", "apple");

        var result = endpoint.listProviders();

        assertThat(result).containsExactlyInAnyOrder("google", "github", "apple");
    }

    private static class StubConnectionProvider implements ServiceConnectionProvider {
        List<ServiceConnection> connections = List.of();
        ServiceConnection singleConnection;
        String disconnectedProvider;
        String disconnectedActor;

        @Override public ServiceConnection getConnection(String actorId, String provider, String tenancyId) { return singleConnection; }
        @Override public List<ServiceConnection> listConnections(String actorId, String tenancyId) { return connections; }
        @Override public ServiceAccessToken getAccessToken(String actorId, String provider, String tenancyId) { throw new UnsupportedOperationException(); }
        @Override public void disconnect(String actorId, String provider, String tenancyId) { disconnectedActor = actorId; disconnectedProvider = provider; }
        @Override public Set<String> missingScopes(String actorId, String provider, String tenancyId) { return Set.of(); }
    }

    private static class StubConsentHandler implements IncrementalConsentHandler {
        ConsentRequest response;
        @Override public ConsentRequest requestAdditionalScopes(String actorId, String provider, String tenancyId, Set<String> missingScopes) { return response; }
    }

    private static class StubScopeRegistry implements ScopeRegistry {
        Set<String> providers = Set.of();
        @Override public void register(String provider, Set<String> scopes, Class<?> consumer) {}
        @Override public Set<String> requiredScopes(String provider) { return Set.of(); }
        @Override public Set<String> requiredScopes(String provider, Class<?> consumer) { return Set.of(); }
        @Override public boolean satisfies(String provider, Set<String> grantedScopes) { return true; }
        @Override public Set<String> missingScopes(String provider, Set<String> grantedScopes) { return Set.of(); }
        @Override public Set<String> registeredProviders() { return providers; }
    }
}
