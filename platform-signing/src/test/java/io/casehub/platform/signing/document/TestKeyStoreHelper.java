package io.casehub.platform.signing.document;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.FileOutputStream;
import java.math.BigInteger;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Date;

public final class TestKeyStoreHelper {

    static final String ALIAS = "test-seal";
    static final String PASSWORD = "changeit";
    static final String DN = "CN=Test Seal, O=CaseHub, C=IE";

    private TestKeyStoreHelper() {}

    public static Path createTestKeystore(Path directory) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(256);
        KeyPair kp = kpg.generateKeyPair();

        X500Name dn = new X500Name(DN);
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withECDSA").build(kp.getPrivate());
        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                dn, BigInteger.ONE,
                Date.from(Instant.now().minusSeconds(3600)),
                Date.from(Instant.now().plusSeconds(86400L * 365)),
                dn, kp.getPublic());
        X509Certificate cert = new JcaX509CertificateConverter().getCertificate(builder.build(signer));

        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, PASSWORD.toCharArray());
        ks.setKeyEntry(ALIAS, kp.getPrivate(), PASSWORD.toCharArray(), new X509Certificate[]{cert});

        Path keystorePath = directory.resolve("test.p12");
        try (FileOutputStream fos = new FileOutputStream(keystorePath.toFile())) {
            ks.store(fos, PASSWORD.toCharArray());
        }
        return keystorePath;
    }

    static byte[] createMinimalPdf() throws Exception {
        var doc = new org.apache.pdfbox.pdmodel.PDDocument();
        doc.addPage(new org.apache.pdfbox.pdmodel.PDPage());
        var os = new java.io.ByteArrayOutputStream();
        doc.save(os);
        doc.close();
        return os.toByteArray();
    }
}
