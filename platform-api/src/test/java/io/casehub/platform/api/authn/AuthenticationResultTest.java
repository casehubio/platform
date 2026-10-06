package io.casehub.platform.api.authn;

import io.casehub.platform.api.identity.PrincipalId;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class AuthenticationResultTest {

    @Test
    void constructor_rejects_null_principal() {
        assertThrows(NullPointerException.class,
            () -> new AuthenticationResult(null, "tenant", Set.of(), "webauthn", Map.of()));
    }

    @Test
    void constructor_rejects_null_tenancyId() {
        assertThrows(NullPointerException.class,
            () -> new AuthenticationResult(PrincipalId.human("alice"), null, Set.of(), "webauthn", Map.of()));
    }

    @Test
    void constructor_rejects_null_method() {
        assertThrows(NullPointerException.class,
            () -> new AuthenticationResult(PrincipalId.human("alice"), "tenant", Set.of(), null, Map.of()));
    }

    @Test
    void constructor_defaults_null_groups_to_empty() {
        var result = new AuthenticationResult(PrincipalId.human("alice"), "tenant", null, "webauthn", Map.of());
        assertEquals(Set.of(), result.groups());
    }

    @Test
    void constructor_defaults_null_metadata_to_empty() {
        var result = new AuthenticationResult(PrincipalId.human("alice"), "tenant", Set.of(), "webauthn", null);
        assertEquals(Map.of(), result.metadata());
    }

    @Test
    void groups_are_immutable_copy() {
        var mutable = new java.util.HashSet<>(Set.of("admin"));
        var result = new AuthenticationResult(PrincipalId.human("alice"), "tenant", mutable, "webauthn", Map.of());
        mutable.add("user");
        assertFalse(result.groups().contains("user"));
    }
}
