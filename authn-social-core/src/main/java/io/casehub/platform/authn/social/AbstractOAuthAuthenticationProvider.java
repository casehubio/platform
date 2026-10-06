package io.casehub.platform.authn.social;

import io.casehub.platform.api.authn.AuthenticationContext;
import io.casehub.platform.api.authn.AuthenticationProvider;
import io.casehub.platform.api.authn.AuthenticationResult;
import io.casehub.platform.api.authn.ChallengeRecord;
import io.casehub.platform.api.authn.ChallengeResponse;
import io.casehub.platform.api.authn.IdentityBinding;
import io.casehub.platform.api.authn.IdentityBindingStore;
import io.casehub.platform.api.authn.InvalidChallengeException;
import io.casehub.platform.api.authn.OAuthTokenRecord;
import io.casehub.platform.api.authn.OAuthTokenStore;
import io.casehub.platform.api.authn.UserResolver;
import io.casehub.platform.api.identity.PrincipalId;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public abstract class AbstractOAuthAuthenticationProvider implements AuthenticationProvider {

    private final OAuthConfig config;
    private final OAuthHttpClient httpClient;
    private final OAuthTokenStore tokenStore;
    private final IdentityBindingStore bindingStore;
    private final UserResolver userResolver;
    private final ConcurrentHashMap<String, String> pendingStates = new ConcurrentHashMap<>();

    protected AbstractOAuthAuthenticationProvider(OAuthConfig config,
                                                   OAuthHttpClient httpClient,
                                                   OAuthTokenStore tokenStore,
                                                   IdentityBindingStore bindingStore,
                                                   UserResolver userResolver) {
        this.config = Objects.requireNonNull(config, "config");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.tokenStore = Objects.requireNonNull(tokenStore, "tokenStore");
        this.bindingStore = Objects.requireNonNull(bindingStore, "bindingStore");
        this.userResolver = Objects.requireNonNull(userResolver, "userResolver");
    }

    protected abstract String authorizationEndpoint();

    protected abstract String tokenEndpoint();

    protected abstract OAuthIdentity extractIdentity(OAuthTokenResponse tokens);

    @Override
    public ChallengeResponse initiate(AuthenticationContext context) {
        String state = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plusSeconds(config.challengeTimeoutSeconds());

        pendingStates.put(state, context.tenancyId());

        Set<String> scopes = config.scopes();
        Object additionalScopes = context.hints().get("additionalScopes");
        if (additionalScopes instanceof String s && !s.isBlank()) {
            var combined = new java.util.LinkedHashSet<>(scopes);
            combined.addAll(Set.of(s.split("[\\s,]+")));
            scopes = combined;
        }

        String authUrl = buildAuthorizationUrl(state, scopes);
        return new OAuthChallengeResponse(state, expiresAt, authUrl);
    }

    @Override
    public AuthenticationResult verify(ChallengeRecord challenge, Map<String, Object> data) {
        String code = (String) data.get("code");
        if (code == null || code.isBlank()) {
            throw new InvalidChallengeException(method(), "Missing authorization code");
        }

        pendingStates.remove(challenge.challengeId());

        var tokenParams = new LinkedHashMap<String, String>();
        tokenParams.put("grant_type", "authorization_code");
        tokenParams.put("code", code);
        tokenParams.put("redirect_uri", config.redirectUri());
        tokenParams.put("client_id", config.clientId());
        tokenParams.put("client_secret", config.clientSecret());

        OAuthTokenResponse tokens = httpClient.exchangeCode(tokenEndpoint(), tokenParams);
        OAuthIdentity identity = extractIdentity(tokens);

        String tenancyId = challenge.tenancyId();
        var existingBinding = bindingStore.findByExternalId(method(), identity.externalId(), tenancyId);

        String actorId;
        boolean firstLogin;
        if (existingBinding.isPresent()) {
            actorId = existingBinding.get().actorId();
            firstLogin = false;
        } else {
            actorId = resolveOrCreateActorId(identity, tenancyId);
            bindingStore.bind(new IdentityBinding(
                    method(), identity.externalId(), actorId, tenancyId,
                    identity.email(), Instant.now()));
            firstLogin = true;
        }

        Set<String> grantedScopes = tokens.scope() != null
                ? Set.of(tokens.scope().split("\\s+"))
                : config.scopes();

        tokenStore.store(new OAuthTokenRecord(
                actorId, tenancyId, method(),
                tokens.accessToken(), tokens.refreshToken(),
                grantedScopes,
                tokens.expiresIn() > 0
                        ? Instant.now().plusSeconds(tokens.expiresIn())
                        : null,
                Instant.now()));

        return new AuthenticationResult(
                PrincipalId.human(actorId),
                tenancyId,
                Set.of(),
                method(),
                Map.of(
                        "externalId", identity.externalId(),
                        "firstLogin", firstLogin));
    }

    private String resolveOrCreateActorId(OAuthIdentity identity, String tenancyId) {
        if (identity.email() != null) {
            var resolved = userResolver.resolveByEmail(identity.email(), tenancyId);
            if (resolved.isPresent()) {
                return resolved.get().id();
            }
        }
        return identity.externalId();
    }

    private String buildAuthorizationUrl(String state, Set<String> scopes) {
        var params = new LinkedHashMap<String, String>();
        params.put("client_id", config.clientId());
        params.put("redirect_uri", config.redirectUri());
        params.put("response_type", "code");
        params.put("scope", String.join(" ", scopes));
        params.put("state", state);

        String query = params.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
                        + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));

        return authorizationEndpoint() + "?" + query;
    }
}
