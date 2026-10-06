package io.casehub.platform.authn.webauthn;

import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CoseKeyParserTest {

    @Test
    void parsesEs256Key() throws Exception {
        var kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec("secp256r1"));
        var kp = kpg.generateKeyPair();
        var ecPub = (ECPublicKey) kp.getPublic();

        byte[] x = toUnsignedFixedLength(ecPub.getW().getAffineX().toByteArray(), 32);
        byte[] y = toUnsignedFixedLength(ecPub.getW().getAffineY().toByteArray(), 32);

        byte[] coseKey = buildCoseEc2Key(-7, 1, x, y);
        var parsed = CoseKeyParser.parse(coseKey);

        assertThat(parsed).isInstanceOf(ECPublicKey.class);
        var parsedEc = (ECPublicKey) parsed;
        assertThat(parsedEc.getW().getAffineX()).isEqualTo(ecPub.getW().getAffineX());
        assertThat(parsedEc.getW().getAffineY()).isEqualTo(ecPub.getW().getAffineY());
    }

    @Test
    void algorithmNameReturnsEs256() throws Exception {
        byte[] coseKey = buildCoseEc2Key(-7, 1, new byte[32], new byte[32]);
        assertThat(CoseKeyParser.algorithmName(coseKey)).isEqualTo("SHA256withECDSA");
    }

    @Test
    void algorithmNameReturnsEs384() throws Exception {
        byte[] coseKey = buildCoseEc2Key(-35, 2, new byte[48], new byte[48]);
        assertThat(CoseKeyParser.algorithmName(coseKey)).isEqualTo("SHA384withECDSA");
    }

    @Test
    void algorithmNameReturnsEs512() throws Exception {
        byte[] coseKey = buildCoseEc2Key(-36, 3, new byte[66], new byte[66]);
        assertThat(CoseKeyParser.algorithmName(coseKey)).isEqualTo("SHA512withECDSA");
    }

    @Test
    void algorithmNameReturnsEdDsa() throws Exception {
        byte[] coseKey = buildCoseOkpKey(-8, 6, new byte[32]);
        assertThat(CoseKeyParser.algorithmName(coseKey)).isEqualTo("Ed25519");
    }

    @Test
    void parsesEdDsaKey() throws Exception {
        var kpg = KeyPairGenerator.getInstance("Ed25519");
        var kp = kpg.generateKeyPair();
        byte[] rawPub = kp.getPublic().getEncoded();
        // Ed25519 SPKI is 44 bytes: 12-byte prefix + 32-byte raw key
        byte[] rawKey = new byte[32];
        System.arraycopy(rawPub, rawPub.length - 32, rawKey, 0, 32);

        byte[] coseKey = buildCoseOkpKey(-8, 6, rawKey);
        var parsed = CoseKeyParser.parse(coseKey);

        assertThat(parsed.getAlgorithm()).isEqualTo("EdDSA");
    }

    @Test
    void rejectsUnsupportedKeyType() throws Exception {
        byte[] coseKey = buildCoseWithKeyType(99);
        assertThatThrownBy(() -> CoseKeyParser.parse(coseKey))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported COSE key type");
    }

    @Test
    void rejectsNullInput() {
        assertThatThrownBy(() -> CoseKeyParser.parse(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsUnsupportedAlgorithm() throws Exception {
        byte[] coseKey = buildCoseEc2Key(-999, 1, new byte[32], new byte[32]);
        assertThatThrownBy(() -> CoseKeyParser.algorithmName(coseKey))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported COSE algorithm");
    }

    static byte[] buildCoseEc2Key(int alg, int crv, byte[] x, byte[] y) throws Exception {
        var bos = new ByteArrayOutputStream();
        var factory = new CBORFactory();
        try (var gen = factory.createGenerator(bos)) {
            gen.writeStartObject();
            gen.writeFieldId(1);  gen.writeNumber(2);   // kty: EC2
            gen.writeFieldId(3);  gen.writeNumber(alg);
            gen.writeFieldId(-1); gen.writeNumber(crv);
            gen.writeFieldId(-2); gen.writeBinary(x);
            gen.writeFieldId(-3); gen.writeBinary(y);
            gen.writeEndObject();
        }
        return bos.toByteArray();
    }

    static byte[] buildCoseOkpKey(int alg, int crv, byte[] x) throws Exception {
        var bos = new ByteArrayOutputStream();
        var factory = new CBORFactory();
        try (var gen = factory.createGenerator(bos)) {
            gen.writeStartObject();
            gen.writeFieldId(1);  gen.writeNumber(1);   // kty: OKP
            gen.writeFieldId(3);  gen.writeNumber(alg);
            gen.writeFieldId(-1); gen.writeNumber(crv);
            gen.writeFieldId(-2); gen.writeBinary(x);
            gen.writeEndObject();
        }
        return bos.toByteArray();
    }

    private static byte[] buildCoseWithKeyType(int kty) throws Exception {
        var bos = new ByteArrayOutputStream();
        var factory = new CBORFactory();
        try (var gen = factory.createGenerator(bos)) {
            gen.writeStartObject();
            gen.writeFieldId(1);  gen.writeNumber(kty);
            gen.writeFieldId(3);  gen.writeNumber(-7);
            gen.writeEndObject();
        }
        return bos.toByteArray();
    }

    private static byte[] toUnsignedFixedLength(byte[] input, int length) {
        if (input.length == length) return input;
        byte[] result = new byte[length];
        if (input.length > length) {
            System.arraycopy(input, input.length - length, result, 0, length);
        } else {
            System.arraycopy(input, 0, result, length - input.length, input.length);
        }
        return result;
    }
}
