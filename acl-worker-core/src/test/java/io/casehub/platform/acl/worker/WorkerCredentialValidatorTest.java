package io.casehub.platform.acl.worker;

import io.casehub.platform.api.acl.AclAction;
import io.casehub.platform.api.acl.ResourceId;
import io.casehub.platform.api.acl.WorkerAction;
import io.casehub.platform.api.acl.WorkerCredential;
import io.casehub.platform.api.acl.WorkerCredentialStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkerCredentialValidatorTest {

    private WorkerCredentialStore store;
    private WorkerCredentialValidator validator;

    @BeforeEach
    void setUp() {
        store = mock(WorkerCredentialStore.class);
        validator = new WorkerCredentialValidator(store);
    }

    @Test
    void unknownToken_rejected401() {
        when(store.lookup("bad")).thenReturn(Optional.empty());
        var result = validator.validate("bad", "t1", Optional.empty());
        assertThat(result).isInstanceOf(ValidationResult.Rejected.class);
        assertThat(((ValidationResult.Rejected) result).status()).isEqualTo(401);
    }

    @Test
    void expiredToken_rejected401() {
        when(store.lookup("tok")).thenReturn(Optional.of(
            credential("tok", "t1", new ResourceId("case", "c1"), Instant.now().minusSeconds(60))));
        var result = validator.validate("tok", "t1", Optional.empty());
        assertThat(result).isInstanceOf(ValidationResult.Rejected.class);
        assertThat(((ValidationResult.Rejected) result).status()).isEqualTo(401);
    }

    @Test
    void tenancyMismatch_rejected403() {
        when(store.lookup("tok")).thenReturn(Optional.of(
            credential("tok", "t1", new ResourceId("case", "c1"), Instant.now().plusSeconds(3600))));
        var result = validator.validate("tok", "t2", Optional.empty());
        assertThat(result).isInstanceOf(ValidationResult.Rejected.class);
        assertThat(((ValidationResult.Rejected) result).status()).isEqualTo(403);
    }

    @Test
    void scopeMismatch_rejected403() {
        when(store.lookup("tok")).thenReturn(Optional.of(
            credential("tok", "t1", new ResourceId("case", "c1"), Instant.now().plusSeconds(3600))));
        var result = validator.validate("tok", "t1", Optional.of(new ResourceId("case", "c2")));
        assertThat(result).isInstanceOf(ValidationResult.Rejected.class);
        assertThat(((ValidationResult.Rejected) result).status()).isEqualTo(403);
    }

    @Test
    void noResourceId_scopeCheckSkipped() {
        var cred = credential("tok", "t1", new ResourceId("case", "c1"), Instant.now().plusSeconds(3600));
        when(store.lookup("tok")).thenReturn(Optional.of(cred));
        var result = validator.validate("tok", "t1", Optional.empty());
        assertThat(result).isInstanceOf(ValidationResult.Accepted.class);
        assertThat(((ValidationResult.Accepted) result).credential()).isEqualTo(cred);
    }

    @Test
    void validCredential_accepted() {
        var rid = new ResourceId("case", "c1");
        var cred = credential("tok", "t1", rid, Instant.now().plusSeconds(3600));
        when(store.lookup("tok")).thenReturn(Optional.of(cred));
        var result = validator.validate("tok", "t1", Optional.of(rid));
        assertThat(result).isInstanceOf(ValidationResult.Accepted.class);
        assertThat(((ValidationResult.Accepted) result).credential()).isEqualTo(cred);
    }

    private WorkerCredential credential(String token, String tenancyId,
            ResourceId resourceId, Instant expiresAt) {
        return new WorkerCredential(token, "actor", resourceId, tenancyId,
            Set.of(new WorkerAction("READ_CONTEXT", AclAction.READ)),
            expiresAt, Instant.now());
    }
}
