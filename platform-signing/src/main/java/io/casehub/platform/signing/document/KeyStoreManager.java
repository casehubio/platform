package io.casehub.platform.signing.document;

import eu.europa.esig.dss.model.DSSException;
import eu.europa.esig.dss.token.DSSPrivateKeyEntry;
import eu.europa.esig.dss.token.Pkcs12SignatureToken;
import eu.europa.esig.dss.token.SignatureTokenConnection;
import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.security.KeyStore;
import java.util.List;

@ApplicationScoped
public class KeyStoreManager {

    private static final Logger LOG = Logger.getLogger(KeyStoreManager.class);

    private final boolean loaded;
    private final Pkcs12SignatureToken token;
    private final String defaultAlias;

    @Inject
    KeyStoreManager(DssSigningConfig config) {
        this(config.keystorePath().orElse(null),
             config.keystorePassword().orElse(null),
             config.keystoreType(),
             config.keyAlias().orElse(null));
    }

    public KeyStoreManager(String path, String password, String type, String alias) {
        if (path == null || path.isBlank()) {
            LOG.warn("No keystore path configured — document signing disabled");
            this.loaded = false;
            this.token = null;
            this.defaultAlias = null;
            return;
        }
        try {
            var protection = new KeyStore.PasswordProtection(
                    password != null ? password.toCharArray() : null);
            this.token = new Pkcs12SignatureToken(path, protection);
            this.defaultAlias = alias;
            var keys = this.token.getKeys();
            if (keys.isEmpty()) {
                throw new IllegalStateException("Keystore at " + path + " contains no key entries");
            }
            this.loaded = true;
        } catch (IOException | DSSException e) {
            throw new IllegalStateException("Failed to load keystore from " + path, e);
        }
    }

    public boolean isLoaded() {
        return loaded;
    }

    public List<DSSPrivateKeyEntry> getKeys() {
        if (!loaded) return List.of();
        return token.getKeys();
    }

    public SignatureTokenConnection getToken() {
        return token;
    }

    public DSSPrivateKeyEntry resolveKey(String tenancyId) {
        if (!loaded) return null;
        var keys = token.getKeys();
        if (keys.isEmpty()) return null;

        if (tenancyId != null) {
            String tenantAlias = tenancyId + "-seal";
            for (var key : keys) {
                if (key.getCertificate().getSubject().getPrincipal().getName()
                        .contains("CN=" + tenantAlias)) {
                    return key;
                }
            }
        }

        if (defaultAlias != null) {
            for (var key : keys) {
                String cn = key.getCertificate().getSubject().getPrincipal().getName();
                if (cn.contains(defaultAlias)) {
                    return key;
                }
            }
        }

        return keys.getFirst();
    }
}
