package io.casehub.platform.api.expression;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Abstract contract test for SecretManager implementations.
 * Extend and implement {@link #secretManager()} with concrete implementations configured with:
 * <ul>
 *   <li>testservice.apiKey=test-key
 *   <li>testservice.endpoint=https://api.example.com
 *   <li>testservice.config.timeout=30
 *   <li>testservice.config.retries=3
 * </ul>
 */
public abstract class SecretManagerContractTest {

    protected abstract SecretManager secretManager();

    @Test
    void resolves_simple_secret_properties() {
        Map<String, Object> secret = secretManager().secret("testservice");
        assertEquals("test-key", secret.get("apiKey"));
        assertEquals("https://api.example.com", secret.get("endpoint"));
    }

    @Test
    void builds_nested_map_for_dotted_keys() {
        Map<String, Object> secret = secretManager().secret("testservice");
        assertNotNull(secret.get("config"));
        @SuppressWarnings("unchecked")
        Map<String, Object> config = (Map<String, Object>) secret.get("config");
        assertEquals("30", config.get("timeout"));
        assertEquals("3", config.get("retries"));
    }

    @Test
    void throws_SecretNotFoundException_for_missing_secret() {
        assertThrows(SecretNotFoundException.class,
                () -> secretManager().secret("nonexistent"));
    }

    @Test
    void throws_SecretNotFoundException_for_empty_prefix() {
        assertThrows(SecretNotFoundException.class,
                () -> secretManager().secret("empty"));
    }
}
