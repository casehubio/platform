package io.casehub.platform.expression;

import io.casehub.platform.api.expression.SecretNotFoundException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class SecretManagerCoreTest {

    @Test
    void secret_returns_flat_properties() {
        var mgr = new SecretManagerCore(new ConfigManagerCoreTest.MapPropertySource(Map.of(
                "casehub.platform.secrets.openai.apiKey", "sk-test",
                "casehub.platform.secrets.openai.orgId", "org-123")));
        Map<String, Object> result = mgr.secret("openai");
        assertThat(result).containsEntry("apiKey", "sk-test").containsEntry("orgId", "org-123");
    }

    @Test
    void secret_returns_nested_map() {
        var mgr = new SecretManagerCore(new ConfigManagerCoreTest.MapPropertySource(Map.of(
                "casehub.platform.secrets.db.primary.host", "localhost",
                "casehub.platform.secrets.db.primary.port", "5432")));
        Map<String, Object> result = mgr.secret("db");
        @SuppressWarnings("unchecked")
        Map<String, Object> primary = (Map<String, Object>) result.get("primary");
        assertThat(primary).containsEntry("host", "localhost").containsEntry("port", "5432");
    }

    @Test
    void secret_throws_when_not_found() {
        var mgr = new SecretManagerCore(new ConfigManagerCoreTest.MapPropertySource(Map.of()));
        assertThatThrownBy(() -> mgr.secret("missing"))
                .isInstanceOf(SecretNotFoundException.class);
    }

    @Test
    void secret_ignores_properties_without_matching_prefix() {
        var mgr = new SecretManagerCore(new ConfigManagerCoreTest.MapPropertySource(Map.of(
                "casehub.platform.secrets.openai.apiKey", "sk-test",
                "casehub.platform.other", "ignored")));
        Map<String, Object> result = mgr.secret("openai");
        assertThat(result).hasSize(1).containsEntry("apiKey", "sk-test");
    }
}
