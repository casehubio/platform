package io.casehub.platform.signing.document;

import eu.europa.esig.dss.model.DSSException;
import eu.europa.esig.dss.token.DSSPrivateKeyEntry;
import eu.europa.esig.dss.token.Pkcs12SignatureToken;
import eu.europa.esig.dss.token.SignatureTokenConnection;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.security.KeyStore;
import java.util.List;

class KeyStoreManager {

    private static final Logger LOG = Logger.getLogger(KeyStoreManager.class);

    private final boolean loaded;
    private final Pkcs12SignatureToken token;
    private final String defaultAlias;

    KeyStoreManager(String path, String password, String type, String alias) {
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

    boolean isLoaded() {
        return loaded;
    }

    List<DSSPrivateKeyEntry> getKeys() {
        if (!loaded) return List.of();
        return token.getKeys();
    }

    SignatureTokenConnection getToken() {
        return token;
    }

    DSSPrivateKeyEntry resolveKey(String tenancyId) {
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
