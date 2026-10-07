package io.casehub.platform.authn.spring;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AuthnSpringPropertiesTest {

    @Test
    void defaults() {
        var props = new AuthnSpringProperties();
        assertThat(props.getAccessTokenTtlSeconds()).isEqualTo(900);
        assertThat(props.getRefreshTokenTtlSeconds()).isEqualTo(2592000);
        assertThat(props.getWebauthn()).isNull();
        assertThat(props.getSocial()).isEmpty();
    }

    @Test
    void webauthnDefaults() {
        var webauthn = new AuthnSpringProperties.WebAuthn();
        assertThat(webauthn.getChallengeTimeoutSeconds()).isEqualTo(300);
        assertThat(webauthn.getChallengeLength()).isEqualTo(32);
        assertThat(webauthn.getAllowedOrigins()).isEmpty();
    }

    @Test
    void webauthnSetters() {
        var webauthn = new AuthnSpringProperties.WebAuthn();
        webauthn.setRpId("example.com");
        webauthn.setRpName("Example");
        webauthn.setAllowedOrigins(Set.of("https://example.com"));

        assertThat(webauthn.getRpId()).isEqualTo("example.com");
        assertThat(webauthn.getRpName()).isEqualTo("Example");
        assertThat(webauthn.getAllowedOrigins()).containsExactly("https://example.com");
    }

    @Test
    void socialProviderSetters() {
        var provider = new AuthnSpringProperties.SocialProvider();
        provider.setClientId("id");
        provider.setClientSecret("secret");
        provider.setRedirectUri("https://example.com/callback");
        provider.setTeamId("team1");
        provider.setKeyId("key1");
        provider.setPrivateKey("pk");

        assertThat(provider.getClientId()).isEqualTo("id");
        assertThat(provider.getClientSecret()).isEqualTo("secret");
        assertThat(provider.getRedirectUri()).isEqualTo("https://example.com/callback");
        assertThat(provider.getTeamId()).isEqualTo("team1");
        assertThat(provider.getKeyId()).isEqualTo("key1");
        assertThat(provider.getPrivateKey()).isEqualTo("pk");
    }

    @Test
    void socialMapBinding() {
        var props = new AuthnSpringProperties();
        var google = new AuthnSpringProperties.SocialProvider();
        google.setClientId("google-id");
        props.setSocial(Map.of("google", google));

        assertThat(props.getSocial()).containsKey("google");
        assertThat(props.getSocial().get("google").getClientId()).isEqualTo("google-id");
    }
}
