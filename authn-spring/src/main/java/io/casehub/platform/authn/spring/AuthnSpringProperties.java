package io.casehub.platform.authn.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;
import java.util.Set;

@ConfigurationProperties(prefix = "casehub.authn")
public class AuthnSpringProperties {

    private long accessTokenTtlSeconds = 900;
    private long refreshTokenTtlSeconds = 2592000;
    private WebAuthn webauthn;
    private Map<String, SocialProvider> social = Map.of();

    public long getAccessTokenTtlSeconds() { return accessTokenTtlSeconds; }
    public void setAccessTokenTtlSeconds(long accessTokenTtlSeconds) { this.accessTokenTtlSeconds = accessTokenTtlSeconds; }

    public long getRefreshTokenTtlSeconds() { return refreshTokenTtlSeconds; }
    public void setRefreshTokenTtlSeconds(long refreshTokenTtlSeconds) { this.refreshTokenTtlSeconds = refreshTokenTtlSeconds; }

    public WebAuthn getWebauthn() { return webauthn; }
    public void setWebauthn(WebAuthn webauthn) { this.webauthn = webauthn; }

    public Map<String, SocialProvider> getSocial() { return social; }
    public void setSocial(Map<String, SocialProvider> social) { this.social = social; }

    public static class WebAuthn {
        private String rpId;
        private String rpName;
        private Set<String> allowedOrigins = Set.of();
        private long challengeTimeoutSeconds = 300;
        private int challengeLength = 32;

        public String getRpId() { return rpId; }
        public void setRpId(String rpId) { this.rpId = rpId; }

        public String getRpName() { return rpName; }
        public void setRpName(String rpName) { this.rpName = rpName; }

        public Set<String> getAllowedOrigins() { return allowedOrigins; }
        public void setAllowedOrigins(Set<String> allowedOrigins) { this.allowedOrigins = allowedOrigins; }

        public long getChallengeTimeoutSeconds() { return challengeTimeoutSeconds; }
        public void setChallengeTimeoutSeconds(long challengeTimeoutSeconds) { this.challengeTimeoutSeconds = challengeTimeoutSeconds; }

        public int getChallengeLength() { return challengeLength; }
        public void setChallengeLength(int challengeLength) { this.challengeLength = challengeLength; }
    }

    public static class SocialProvider {
        private String clientId;
        private String clientSecret;
        private String redirectUri;
        private String teamId;
        private String keyId;
        private String privateKey;

        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }

        public String getClientSecret() { return clientSecret; }
        public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }

        public String getRedirectUri() { return redirectUri; }
        public void setRedirectUri(String redirectUri) { this.redirectUri = redirectUri; }

        public String getTeamId() { return teamId; }
        public void setTeamId(String teamId) { this.teamId = teamId; }

        public String getKeyId() { return keyId; }
        public void setKeyId(String keyId) { this.keyId = keyId; }

        public String getPrivateKey() { return privateKey; }
        public void setPrivateKey(String privateKey) { this.privateKey = privateKey; }
    }
}
