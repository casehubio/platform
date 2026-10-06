package io.casehub.platform.authn;

import io.casehub.platform.api.authn.JwtSigningKeyResolver;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import java.util.stream.Collectors;

public class JwtIssuerCore {

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

    private final JwtSigningKeyResolver keyResolver;

    public JwtIssuerCore(JwtSigningKeyResolver keyResolver) {
        this.keyResolver = keyResolver;
    }

    public String issue(String tenancyId, String sessionId, String actorId,
                        Set<String> groups, String method, long ttlSeconds) {
        var keyPair = keyResolver.signingKeyPair(tenancyId);
        var keyId = keyResolver.keyId(tenancyId);
        var alg = algorithmFor(keyPair);
        var now = Instant.now();

        var header = "{\"alg\":\"" + alg.jwtName + "\",\"typ\":\"JWT\",\"kid\":\"" + escapeJson(keyId) + "\"}";
        var payload = buildPayload(sessionId, actorId, tenancyId, groups, method, now, ttlSeconds);

        var headerB64 = B64.encodeToString(header.getBytes(StandardCharsets.UTF_8));
        var payloadB64 = B64.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        var signingInput = headerB64 + "." + payloadB64;

        var signature = sign(signingInput.getBytes(StandardCharsets.UTF_8), keyPair, alg);
        return signingInput + "." + B64.encodeToString(signature);
    }

    private static String buildPayload(String sessionId, String actorId, String tenancyId,
                                       Set<String> groups, String method,
                                       Instant now, long ttlSeconds) {
        var sb = new StringBuilder(256);
        sb.append("{\"iss\":\"casehub\"");
        sb.append(",\"sub\":\"").append(escapeJson(actorId)).append('"');
        sb.append(",\"aud\":\"casehub\"");
        sb.append(",\"iat\":").append(now.getEpochSecond());
        sb.append(",\"exp\":").append(now.plusSeconds(ttlSeconds).getEpochSecond());
        sb.append(",\"sid\":\"").append(escapeJson(sessionId)).append('"');
        sb.append(",\"tid\":\"").append(escapeJson(tenancyId)).append('"');
        sb.append(",\"mth\":\"").append(escapeJson(method)).append('"');
        sb.append(",\"groups\":").append(jsonArray(groups));
        sb.append('}');
        return sb.toString();
    }

    private static String jsonArray(Set<String> values) {
        if (values.isEmpty()) return "[]";
        return "[" + values.stream()
                .map(v -> "\"" + escapeJson(v) + "\"")
                .collect(Collectors.joining(",")) + "]";
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static byte[] sign(byte[] data, KeyPair keyPair, JwtAlgorithm alg) {
        try {
            var sig = Signature.getInstance(alg.jcaName);
            sig.initSign(keyPair.getPrivate());
            sig.update(data);
            return sig.sign();
        } catch (Exception e) {
            throw new IllegalStateException("JWT signing failed", e);
        }
    }

    private static JwtAlgorithm algorithmFor(KeyPair keyPair) {
        return switch (keyPair.getPublic().getAlgorithm()) {
            case "RSA" -> JwtAlgorithm.RS256;
            case "EC" -> JwtAlgorithm.ES256;
            default -> throw new UnsupportedOperationException(
                    "Unsupported key algorithm: " + keyPair.getPublic().getAlgorithm());
        };
    }

    private enum JwtAlgorithm {
        RS256("RS256", "SHA256withRSA"),
        ES256("ES256", "SHA256withECDSA");

        final String jwtName;
        final String jcaName;

        JwtAlgorithm(String jwtName, String jcaName) {
            this.jwtName = jwtName;
            this.jcaName = jcaName;
        }
    }
}
