package io.casehub.platform.credentials.spring;

import io.casehub.platform.api.credentials.CredentialPropertyKeys;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EnvironmentCredentialResolverTest {

    @Test
    void resolves_all_standard_keys() {
        var env = new MockEnvironment()
                .withProperty("my-creds.user", "alice")
                .withProperty("my-creds.password", "secret123")
                .withProperty("my-creds.bearer-token", "tok_abc")
                .withProperty("my-creds.api-key", "ak_xyz");

        var resolver = new EnvironmentCredentialResolver(env);
        Map<String, String> result = resolver.resolve("my-creds");

        assertThat(result).containsEntry(CredentialPropertyKeys.USER, "alice");
        assertThat(result).containsEntry(CredentialPropertyKeys.PASSWORD, "secret123");
        assertThat(result).containsEntry(CredentialPropertyKeys.BEARER_TOKEN, "tok_abc");
        assertThat(result).containsEntry(CredentialPropertyKeys.API_KEY, "ak_xyz");
        assertThat(result).doesNotContainKey(CredentialPropertyKeys.EXPIRES_AT);
        assertThat(result).doesNotContainKey(CredentialPropertyKeys.SIGNING_SECRET);
    }

    @Test
    void null_ref_returns_empty() {
        var resolver = new EnvironmentCredentialResolver(new MockEnvironment());
        assertThat(resolver.resolve(null)).isEmpty();
    }

    @Test
    void blank_ref_returns_empty() {
        var resolver = new EnvironmentCredentialResolver(new MockEnvironment());
        assertThat(resolver.resolve("  ")).isEmpty();
    }

    @Test
    void unknown_ref_returns_empty() {
        var resolver = new EnvironmentCredentialResolver(new MockEnvironment());
        assertThat(resolver.resolve("nonexistent")).isEmpty();
    }

    @Test
    void partial_keys_returns_only_present() {
        var env = new MockEnvironment()
                .withProperty("partial.api-key", "ak_only");

        var resolver = new EnvironmentCredentialResolver(env);
        Map<String, String> result = resolver.resolve("partial");

        assertThat(result).hasSize(1);
        assertThat(result).containsEntry(CredentialPropertyKeys.API_KEY, "ak_only");
    }
}
