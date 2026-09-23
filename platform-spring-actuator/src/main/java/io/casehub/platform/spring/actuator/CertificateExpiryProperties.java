package io.casehub.platform.spring.actuator;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "casehub.signing")
record CertificateExpiryProperties(
        String keystorePath,
        String keystorePassword,
        String keystoreType,
        int expiryWarningDays) {

    CertificateExpiryProperties {
        if (keystoreType == null) keystoreType = "PKCS12";
        if (expiryWarningDays <= 0) expiryWarningDays = 30;
    }
}
