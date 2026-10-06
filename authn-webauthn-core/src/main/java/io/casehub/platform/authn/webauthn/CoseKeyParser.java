package io.casehub.platform.authn.webauthn;

import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;

import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.EdECPoint;
import java.security.spec.EdECPublicKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.NamedParameterSpec;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class CoseKeyParser {

    private static final CBORFactory CBOR_FACTORY = new CBORFactory();

    private static final int KTY_OKP = 1;
    private static final int KTY_EC2 = 2;

    private static final int LABEL_KTY = 1;
    private static final int LABEL_ALG = 3;
    private static final int LABEL_CRV = -1;
    private static final int LABEL_X = -2;
    private static final int LABEL_Y = -3;

    private static final int ALG_ES256 = -7;
    private static final int ALG_ES384 = -35;
    private static final int ALG_ES512 = -36;
    private static final int ALG_EDDSA = -8;

    private static final int CRV_P256 = 1;
    private static final int CRV_P384 = 2;
    private static final int CRV_P521 = 3;
    private static final int CRV_ED25519 = 6;

    private CoseKeyParser() {}

    public static PublicKey parse(byte[] coseKey) {
        Objects.requireNonNull(coseKey, "coseKey");
        Map<Integer, Object> fields = parseCborMap(coseKey);

        int kty = intField(fields, LABEL_KTY, "kty");
        return switch (kty) {
            case KTY_EC2 -> parseEc2Key(fields);
            case KTY_OKP -> parseOkpKey(fields);
            default -> throw new IllegalArgumentException("Unsupported COSE key type: " + kty);
        };
    }

    public static String algorithmName(byte[] coseKey) {
        Objects.requireNonNull(coseKey, "coseKey");
        Map<Integer, Object> fields = parseCborMap(coseKey);
        int alg = intField(fields, LABEL_ALG, "alg");
        return switch (alg) {
            case ALG_ES256 -> "SHA256withECDSA";
            case ALG_ES384 -> "SHA384withECDSA";
            case ALG_ES512 -> "SHA512withECDSA";
            case ALG_EDDSA -> "Ed25519";
            default -> throw new IllegalArgumentException("Unsupported COSE algorithm: " + alg);
        };
    }

    private static PublicKey parseEc2Key(Map<Integer, Object> fields) {
        int crv = intField(fields, LABEL_CRV, "crv");
        byte[] x = bytesField(fields, LABEL_X, "x");
        byte[] y = bytesField(fields, LABEL_Y, "y");

        ECParameterSpec params = ecParameterSpec(crv);
        ECPoint point = new ECPoint(
                new BigInteger(1, x),
                new BigInteger(1, y));

        try {
            return KeyFactory.getInstance("EC")
                    .generatePublic(new ECPublicKeySpec(point, params));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalArgumentException("Failed to construct EC public key", e);
        }
    }

    private static PublicKey parseOkpKey(Map<Integer, Object> fields) {
        int crv = intField(fields, LABEL_CRV, "crv");
        byte[] x = bytesField(fields, LABEL_X, "x");

        if (crv != CRV_ED25519) {
            throw new IllegalArgumentException("Unsupported OKP curve: " + crv);
        }

        byte[] reversed = new byte[x.length];
        for (int i = 0; i < x.length; i++) {
            reversed[i] = x[x.length - 1 - i];
        }
        boolean xOdd = (x[x.length - 1] & 1) != 0;
        BigInteger y = new BigInteger(1, reversed);
        EdECPoint point = new EdECPoint(xOdd, y);

        try {
            return KeyFactory.getInstance("Ed25519")
                    .generatePublic(new EdECPublicKeySpec(NamedParameterSpec.ED25519, point));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalArgumentException("Failed to construct Ed25519 public key", e);
        }
    }

    private static ECParameterSpec ecParameterSpec(int crv) {
        String curveName = switch (crv) {
            case CRV_P256 -> "secp256r1";
            case CRV_P384 -> "secp384r1";
            case CRV_P521 -> "secp521r1";
            default -> throw new IllegalArgumentException("Unsupported EC curve: " + crv);
        };
        try {
            var kpg = java.security.KeyPairGenerator.getInstance("EC");
            kpg.initialize(new java.security.spec.ECGenParameterSpec(curveName));
            var kp = kpg.generateKeyPair();
            return ((java.security.interfaces.ECPublicKey) kp.getPublic()).getParams();
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to obtain EC parameters for curve " + curveName, e);
        }
    }

    private static Map<Integer, Object> parseCborMap(byte[] data) {
        try (var parser = CBOR_FACTORY.createParser(data)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw new IllegalArgumentException("COSE key must be a CBOR map");
            }
            Map<Integer, Object> fields = new HashMap<>();
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                int key = Integer.parseInt(parser.currentName());
                parser.nextToken();
                Object value = switch (parser.currentToken()) {
                    case VALUE_NUMBER_INT -> parser.getIntValue();
                    case VALUE_EMBEDDED_OBJECT -> parser.getBinaryValue();
                    default -> parser.getText();
                };
                fields.put(key, value);
            }
            return fields;
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid CBOR data for COSE key", e);
        }
    }

    private static int intField(Map<Integer, Object> fields, int label, String name) {
        Object val = fields.get(label);
        if (val == null) {
            throw new IllegalArgumentException("Missing COSE key field: " + name + " (label " + label + ")");
        }
        if (val instanceof Number n) {
            return n.intValue();
        }
        throw new IllegalArgumentException("COSE key field " + name + " is not an integer");
    }

    private static byte[] bytesField(Map<Integer, Object> fields, int label, String name) {
        Object val = fields.get(label);
        if (val == null) {
            throw new IllegalArgumentException("Missing COSE key field: " + name + " (label " + label + ")");
        }
        if (val instanceof byte[] bytes) {
            return bytes;
        }
        throw new IllegalArgumentException("COSE key field " + name + " is not a byte string");
    }
}
