package io.casehub.platform.acl.worker;

import io.casehub.platform.api.acl.WorkerCredential;

public sealed interface ValidationResult {

    record Accepted(WorkerCredential credential) implements ValidationResult {}
    record Rejected(int status, String message) implements ValidationResult {}

    static ValidationResult accept(WorkerCredential credential) { return new Accepted(credential); }
    static ValidationResult reject(int status, String message) { return new Rejected(status, message); }
}
