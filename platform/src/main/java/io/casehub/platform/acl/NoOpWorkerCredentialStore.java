package io.casehub.platform.acl;

import io.casehub.platform.api.acl.WorkerCredentialStore;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

@DefaultBean
@ApplicationScoped
public class NoOpWorkerCredentialStore implements WorkerCredentialStore {
}
