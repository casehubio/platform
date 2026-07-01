package io.casehub.platform.api.identity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VerificationMethodTypeTest {

    @Test
    void ed25519_constant_matches_w3c_type() {
        assertEquals("Ed25519VerificationKey2020", VerificationMethodType.ED25519);
    }

    @Test
    void p256_constant_matches_w3c_type() {
        assertEquals("EcdsaSecp256r1VerificationKey2019", VerificationMethodType.P256);
    }
}
