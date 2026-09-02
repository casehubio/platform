package io.casehub.platform.signing.lifecycle;

import io.casehub.platform.signing.document.KeyStoreManager;
import org.jboss.logging.Logger;

import java.util.concurrent.atomic.AtomicReference;

public class KeyStoreRotationService {

    private static final Logger LOG = Logger.getLogger(KeyStoreRotationService.class);

    private final AtomicReference<KeyStoreManager> managerRef;

    public KeyStoreRotationService(AtomicReference<KeyStoreManager> managerRef) {
        this.managerRef = managerRef;
    }

    public boolean rotate(String path, String password, String type, String alias) {
        try {
            var replacement = new KeyStoreManager(path, password, type, alias);
            if (!replacement.isLoaded()) {
                LOG.warn("Rotation failed — new keystore did not load");
                return false;
            }
            var previous = managerRef.getAndSet(replacement);
            LOG.infof("Certificate rotated: %s → %s",
                    previous != null && previous.isLoaded()
                            ? previous.getKeys().getFirst().getCertificate().getSubject().getPrincipal().getName()
                            : "none",
                    replacement.getKeys().getFirst().getCertificate().getSubject().getPrincipal().getName());
            return true;
        } catch (Exception e) {
            LOG.warnf("Rotation failed — keeping existing keystore: %s", e.getMessage());
            return false;
        }
    }
}
