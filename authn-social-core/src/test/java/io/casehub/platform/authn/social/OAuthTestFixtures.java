package io.casehub.platform.authn.social;

import io.casehub.platform.api.authn.AuthenticationEventListener;
import io.casehub.platform.api.authn.IdentityBinding;
import io.casehub.platform.api.authn.IdentityBindingStore;
import io.casehub.platform.api.authn.OAuthTokenRecord;
import io.casehub.platform.api.authn.OAuthTokenStore;
import io.casehub.platform.api.authn.UserResolver;
import io.casehub.platform.api.identity.PrincipalId;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class OAuthTestFixtures {

    static final AuthenticationEventListener NO_OP_LISTENER = new AuthenticationEventListener() {};

    private OAuthTestFixtures() {}

    static String buildIdToken(String sub, String email, String name, boolean emailVerified) {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes());
        StringBuilder payloadJson = new StringBuilder("{\"sub\":\"").append(sub).append("\"");
        if (email != null) {
            payloadJson.append(",\"email\":\"").append(email).append("\"");
        }
        payloadJson.append(",\"email_verified\":").append(emailVerified);
        if (name != null) {
            payloadJson.append(",\"name\":\"").append(name).append("\"");
        }
        payloadJson.append("}");
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payloadJson.toString().getBytes());
        String signature = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("fake-signature".getBytes());
        return header + "." + payload + "." + signature;
    }

    static class StubOAuthHttpClient implements OAuthHttpClient {
        OAuthTokenResponse nextTokenResponse;
        Map<String, Object> nextUserInfo;

        @Override
        public OAuthTokenResponse exchangeCode(String tokenEndpoint, Map<String, String> params) {
            return nextTokenResponse;
        }

        @Override
        public Map<String, Object> fetchUserInfo(String userInfoEndpoint, String accessToken) {
            return nextUserInfo != null ? nextUserInfo : Map.of();
        }
    }

    static class StubUserResolver implements UserResolver {
        @Override
        public Optional<PrincipalId> resolveByEmail(String email, String tenancyId) {
            return Optional.empty();
        }
    }

    static class InMemOAuthTokenStore implements OAuthTokenStore {
        final ConcurrentHashMap<String, OAuthTokenRecord> stored = new ConcurrentHashMap<>();

        @Override
        public void store(OAuthTokenRecord record) {
            stored.put(key(record.actorId(), record.provider(), record.tenancyId()), record);
        }

        @Override
        public Optional<OAuthTokenRecord> findByActorId(String actorId, String provider, String tenancyId) {
            return Optional.ofNullable(stored.get(key(actorId, provider, tenancyId)));
        }

        @Override
        public List<OAuthTokenRecord> findAllByActorId(String actorId, String tenancyId) {
            return stored.values().stream()
                    .filter(r -> r.actorId().equals(actorId) && r.tenancyId().equals(tenancyId))
                    .toList();
        }

        @Override
        public void delete(String actorId, String provider, String tenancyId) {
            stored.remove(key(actorId, provider, tenancyId));
        }

        @Override
        public void updateTokens(String actorId, String provider, String tenancyId,
                                  String accessToken, String refreshToken, Instant expiresAt) {
            stored.computeIfPresent(key(actorId, provider, tenancyId),
                    (k, old) -> new OAuthTokenRecord(actorId, tenancyId, provider,
                            accessToken, refreshToken, old.grantedScopes(), expiresAt, old.createdAt()));
        }

        @Override
        public void updateScopes(String actorId, String provider, String tenancyId, Set<String> scopes) {
            stored.computeIfPresent(key(actorId, provider, tenancyId),
                    (k, old) -> new OAuthTokenRecord(actorId, tenancyId, provider,
                            old.accessToken(), old.refreshToken(), scopes, old.expiresAt(), old.createdAt()));
        }

        private static String key(String a, String p, String t) {
            return a + ":" + p + ":" + t;
        }
    }

    static class InMemIdentityBindingStore implements IdentityBindingStore {
        final ConcurrentHashMap<String, IdentityBinding> stored = new ConcurrentHashMap<>();

        @Override
        public void bind(IdentityBinding binding) {
            stored.put(binding.provider() + ":" + binding.externalId() + ":" + binding.tenancyId(), binding);
        }

        @Override
        public Optional<IdentityBinding> findByExternalId(String provider, String externalId, String tenancyId) {
            return Optional.ofNullable(stored.get(provider + ":" + externalId + ":" + tenancyId));
        }

        @Override
        public Optional<IdentityBinding> findByActorId(String actorId, String provider, String tenancyId) {
            return stored.values().stream()
                    .filter(b -> b.actorId().equals(actorId) && b.provider().equals(provider)
                            && b.tenancyId().equals(tenancyId))
                    .findFirst();
        }

        @Override
        public void unbind(String provider, String externalId, String tenancyId) {
            stored.remove(provider + ":" + externalId + ":" + tenancyId);
        }
    }
}
