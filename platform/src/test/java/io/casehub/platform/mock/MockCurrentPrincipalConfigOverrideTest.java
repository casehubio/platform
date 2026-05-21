package io.casehub.platform.mock;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.junit.QuarkusTestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link MockCurrentPrincipal} reads {@code tenancyId} and
 * {@code crossTenantAdmin} from config — not just returns hardcoded defaults.
 */
@QuarkusTest
@TestProfile(MockCurrentPrincipalConfigOverrideTest.OverrideProfile.class)
class MockCurrentPrincipalConfigOverrideTest {

    public static class OverrideProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                "casehub.tenancy.default-id", "custom-tenant-id",
                "casehub.platform.principal.crossTenantAdmin", "true"
            );
        }
    }

    @Inject CurrentPrincipal principal;

    @Test
    void tenancyId_reads_from_config_property() {
        assertEquals("custom-tenant-id", principal.tenancyId());
    }

    @Test
    void isCrossTenantAdmin_reads_from_config_property() {
        assertTrue(principal.isCrossTenantAdmin());
    }
}
