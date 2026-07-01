package io.casehub.platform.identity;

import io.casehub.platform.api.identity.VerificationMethodType;

import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.util.Optional;

/**
 * Maps multicodec key-type codes to SPKI encoding logic.
 *
 * <p>Each variant knows its multicodec code, expected raw-key length, and
 * how to produce canonical SPKI (X.509 SubjectPublicKeyInfo) bytes from the
 * raw key material found in a {@code did:key} DID.
 *
 * <p>For P-256, the raw key is SEC1 compressed (33 bytes: 0x02/0x03 prefix + 32 X bytes).
 * {@link #toSpki(byte[])} decompresses the point and constructs a standard 91-byte
 * uncompressed SPKI via {@code KeyFactory}, ensuring byte-exact equality with
 * {@code PublicKey.getEncoded()}.
 */
enum MulticodecKeyType {

    ED25519(0xed, VerificationMethodType.ED25519, 32) {
        private static final byte[] SPKI_PREFIX = {
                0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00
        };

        @Override
        byte[] toSpki(byte[] rawKey) {
            byte[] spki = new byte[SPKI_PREFIX.length + rawKey.length];
            System.arraycopy(SPKI_PREFIX, 0, spki, 0, SPKI_PREFIX.length);
            System.arraycopy(rawKey, 0, spki, SPKI_PREFIX.length, rawKey.length);
            return spki;
        }
    },

    P256(0x1200, VerificationMethodType.P256, 33) {
        private static final ECParameterSpec EC_PARAMS;
        static {
            try {
                AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
                params.init(new ECGenParameterSpec("secp256r1"));
                EC_PARAMS = params.getParameterSpec(ECParameterSpec.class);
            } catch (Exception e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        @Override
        byte[] toSpki(byte[] rawKey) {
            if (rawKey[0] != 0x02 && rawKey[0] != 0x03) {
                throw new IllegalArgumentException("Invalid SEC1 compression prefix: " + rawKey[0]);
            }
            boolean yOdd = rawKey[0] == 0x03;
            byte[] xBytes = new byte[32];
            System.arraycopy(rawKey, 1, xBytes, 0, 32);
            BigInteger x = new BigInteger(1, xBytes);

            BigInteger p = ((java.security.spec.ECFieldFp) EC_PARAMS.getCurve().getField()).getP();
            BigInteger a = EC_PARAMS.getCurve().getA();
            BigInteger b = EC_PARAMS.getCurve().getB();

            // Y^2 = X^3 + aX + b (mod p)
            BigInteger ySquared = x.modPow(BigInteger.valueOf(3), p)
                    .add(a.multiply(x).mod(p))
                    .add(b)
                    .mod(p);

            // P-256 has p = 3 (mod 4), so Y = (Y^2)^((p+1)/4) mod p
            BigInteger y = ySquared.modPow(p.add(BigInteger.ONE).shiftRight(2), p);

            // Sign selection: match the SEC1 prefix parity
            if (y.testBit(0) != yOdd) {
                y = p.subtract(y);
            }

            try {
                ECPublicKeySpec spec = new ECPublicKeySpec(new ECPoint(x, y), EC_PARAMS);
                return KeyFactory.getInstance("EC").generatePublic(spec).getEncoded();
            } catch (Exception e) {
                throw new IllegalArgumentException("Failed to construct P-256 public key", e);
            }
        }
    };

    final int code;
    final String vmType;
    final int rawKeyLength;

    MulticodecKeyType(int code, String vmType, int rawKeyLength) {
        this.code = code;
        this.vmType = vmType;
        this.rawKeyLength = rawKeyLength;
    }

    abstract byte[] toSpki(byte[] rawKey);

    static Optional<MulticodecKeyType> fromCode(int code) {
        for (MulticodecKeyType type : values()) {
            if (type.code == code) return Optional.of(type);
        }
        return Optional.empty();
    }
}
