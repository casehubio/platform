package io.casehub.platform.acl.worker;

import io.casehub.platform.api.acl.ResourceId;
import io.casehub.platform.api.acl.WorkerCredentialStore;

import java.util.Optional;

public class WorkerCredentialValidator {

    private final WorkerCredentialStore credentialStore;

    public WorkerCredentialValidator(WorkerCredentialStore credentialStore) {
        this.credentialStore = credentialStore;
    }

    public ValidationResult validate(String token, String requestTenancyId, Optional<ResourceId> requestResourceId) {
        var credential = credentialStore.lookup(token);
        if (credential.isEmpty()) {
            return ValidationResult.reject(401, "Invalid worker credential");
        }

        var cred = credential.get();
        if (cred.isExpired()) {
            return ValidationResult.reject(401, "Worker credential expired");
        }

        if (!cred.tenancyId().equals(requestTenancyId)) {
            return ValidationResult.reject(403, "Credential not scoped for this tenant");
        }

        if (requestResourceId.isPresent() && !cred.resourceId().equals(requestResourceId.get())) {
            return ValidationResult.reject(403, "Credential not scoped for this resource");
        }

        return ValidationResult.accept(cred);
    }
}
