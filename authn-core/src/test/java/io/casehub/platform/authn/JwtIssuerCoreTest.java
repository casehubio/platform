package io.casehub.platform.authn;

import io.casehub.platform.api.authn.JwtSigningKeyResolver;
import io.casehub.platform.api.authn.PublicKeyDescriptor;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class JwtIssuerCoreTest {

    private static final Base64.Decoder B64 = Base64.getUrlDecoder();

    @Test
    void issuesValidRsaJwt() throws Exception {
        var keyPair = generateRsaKeyPair();
        var issuer = new JwtIssuerCore(stubResolver(keyPair, "key-1"));

        var jwt = issuer.issue("tenant-1", "session-1", "human:user-1", Set.of("admin"), "webauthn", 3600);

        var parts = jwt.split("\\.");
        assertThat(parts).hasSize(3);

        var header = new String(B64.decode(parts[0]), StandardCharsets.UTF_8);
        assertThat(header).contains("\"alg\":\"RS256\"");
        assertThat(header).contains("\"kid\":\"key-1\"");

        var payload = new String(B64.decode(parts[1]), StandardCharsets.UTF_8);
        assertThat(payload).contains("\"sub\":\"human:user-1\"");
        assertThat(payload).contains("\"tid\":\"tenant-1\"");
        assertThat(payload).contains("\"sid\":\"session-1\"");
        assertThat(payload).contains("\"mth\":\"webauthn\"");
        assertThat(payload).contains("\"admin\"");

        var signingInput = (parts[0] + "." + parts[1]).getBytes(StandardCharsets.UTF_8);
        var sig = Signature.getInstance("SHA256withRSA");
        sig.initVerify(keyPair.getPublic());
        sig.update(signingInput);
        assertThat(sig.verify(B64.decode(parts[2]))).isTrue();
    }

    @Test
    void issuesValidEcJwt() throws Exception {
        var keyPair = generateEcKeyPair();
        var issuer = new JwtIssuerCore(stubResolver(keyPair, "ec-key-1"));

        var jwt = issuer.issue("tenant-1", "session-1", "human:user-1", Set.of(), "google", 1800);

        var parts = jwt.split("\\.");
        assertThat(parts).hasSize(3);

        var header = new String(B64.decode(parts[0]), StandardCharsets.UTF_8);
        assertThat(header).contains("\"alg\":\"ES256\"");

        var signingInput = (parts[0] + "." + parts[1]).getBytes(StandardCharsets.UTF_8);
        var sig = Signature.getInstance("SHA256withECDSA");
        sig.initVerify(keyPair.getPublic());
        sig.update(signingInput);
        assertThat(sig.verify(B64.decode(parts[2]))).isTrue();
    }

    @Test
    void emptyGroupsProducesEmptyArray() {
        var keyPair = generateRsaKeyPair();
        var issuer = new JwtIssuerCore(stubResolver(keyPair, "key-1"));

        var jwt = issuer.issue("tenant-1", "session-1", "human:user-1", Set.of(), "webauthn", 3600);
        var payload = new String(B64.decode(jwt.split("\\.")[1]), StandardCharsets.UTF_8);
        assertThat(payload).contains("\"groups\":[]");
    }

    private static KeyPair generateRsaKeyPair() {
        try {
            var gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            return gen.generateKeyPair();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static KeyPair generateEcKeyPair() {
        try {
            var gen = KeyPairGenerator.getInstance("EC");
            gen.initialize(256);
            return gen.generateKeyPair();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static JwtSigningKeyResolver stubResolver(KeyPair keyPair, String keyId) {
        return new JwtSigningKeyResolver() {
            @Override public KeyPair signingKeyPair(String tenancyId) { return keyPair; }
            @Override public String keyId(String tenancyId) { return keyId; }
            @Override public List<PublicKeyDescriptor> publicKeys() { return List.of(); }
        };
    }
}
