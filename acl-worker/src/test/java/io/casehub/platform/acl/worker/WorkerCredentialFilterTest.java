package io.casehub.platform.acl.worker;

import static org.mockito.Mockito.*;

import io.casehub.platform.api.acl.AclAction;
import io.casehub.platform.api.acl.ResourceId;
import io.casehub.platform.api.acl.WorkerAction;
import io.casehub.platform.api.acl.WorkerCredential;
import io.casehub.platform.api.acl.WorkerCredentialStore;
import io.casehub.platform.api.identity.CurrentPrincipal;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WorkerCredentialFilterTest {

    private WorkerCredentialStore credentialStore;
    private WorkerScopeExtractor scopeExtractor;
    private CurrentPrincipal currentPrincipal;
    private WorkerCredentialFilter filter;
    private ContainerRequestContext ctx;

    @BeforeEach
    void setUp() {
        credentialStore = mock(WorkerCredentialStore.class);
        scopeExtractor = mock(WorkerScopeExtractor.class);
        currentPrincipal = mock(CurrentPrincipal.class);
        filter = new WorkerCredentialFilter(credentialStore, scopeExtractor, currentPrincipal);
        ctx = mock(ContainerRequestContext.class);
    }

    @Test
    void noHeader_passesThrough() {
        when(ctx.getHeaderString("X-Worker-Credential")).thenReturn(null);
        filter.filter(ctx);
        verify(ctx, never()).abortWith(any());
    }

    @Test
    void unknownToken_returns401() {
        when(ctx.getHeaderString("X-Worker-Credential")).thenReturn("bad-token");
        when(credentialStore.lookup("bad-token")).thenReturn(Optional.empty());
        filter.filter(ctx);
        verify(ctx).abortWith(argThat(r -> r.getStatus() == 401));
    }

    @Test
    void expiredToken_returns401() {
        when(ctx.getHeaderString("X-Worker-Credential")).thenReturn("tok");
        when(credentialStore.lookup("tok")).thenReturn(Optional.of(
            credential("tok", "actor", new ResourceId("case", "c1"), "tenant-1",
                Instant.now().minusSeconds(60))));
        filter.filter(ctx);
        verify(ctx).abortWith(argThat(r -> r.getStatus() == 401));
    }

    @Test
    void tenancyMismatch_returns403() {
        when(ctx.getHeaderString("X-Worker-Credential")).thenReturn("tok");
        when(credentialStore.lookup("tok")).thenReturn(Optional.of(
            credential("tok", "actor", new ResourceId("case", "c1"), "tenant-1",
                Instant.now().plusSeconds(3600))));
        when(currentPrincipal.tenancyId()).thenReturn("tenant-2");
        filter.filter(ctx);
        verify(ctx).abortWith(argThat(r -> r.getStatus() == 403));
    }

    @Test
    void scopeMismatch_returns403() {
        when(ctx.getHeaderString("X-Worker-Credential")).thenReturn("tok");
        when(credentialStore.lookup("tok")).thenReturn(Optional.of(
            credential("tok", "actor", new ResourceId("case", "c1"), "tenant-1",
                Instant.now().plusSeconds(3600))));
        when(currentPrincipal.tenancyId()).thenReturn("tenant-1");
        when(scopeExtractor.extractResourceId(ctx))
            .thenReturn(Optional.of(new ResourceId("case", "c2")));
        filter.filter(ctx);
        verify(ctx).abortWith(argThat(r -> r.getStatus() == 403));
    }

    @Test
    void scopeExtractorReturnsEmpty_scopeCheckSkipped() {
        var cred = credential("tok", "actor", new ResourceId("case", "c1"), "tenant-1",
            Instant.now().plusSeconds(3600));
        when(ctx.getHeaderString("X-Worker-Credential")).thenReturn("tok");
        when(credentialStore.lookup("tok")).thenReturn(Optional.of(cred));
        when(currentPrincipal.tenancyId()).thenReturn("tenant-1");
        when(scopeExtractor.extractResourceId(ctx)).thenReturn(Optional.empty());
        filter.filter(ctx);
        verify(ctx, never()).abortWith(any());
        verify(ctx).setProperty("workerCredential", cred);
    }

    @Test
    void validCredential_setsProperty() {
        var rid = new ResourceId("case", "c1");
        var cred = credential("tok", "actor", rid, "tenant-1", Instant.now().plusSeconds(3600));
        when(ctx.getHeaderString("X-Worker-Credential")).thenReturn("tok");
        when(credentialStore.lookup("tok")).thenReturn(Optional.of(cred));
        when(currentPrincipal.tenancyId()).thenReturn("tenant-1");
        when(scopeExtractor.extractResourceId(ctx)).thenReturn(Optional.of(rid));
        filter.filter(ctx);
        verify(ctx, never()).abortWith(any());
        verify(ctx).setProperty("workerCredential", cred);
    }

    @Test
    void failClosedDefault_rejectsAnyCredential() {
        var failClosed = new FailClosedWorkerScopeExtractor();
        var failFilter = new WorkerCredentialFilter(credentialStore, failClosed, currentPrincipal);
        var rid = new ResourceId("case", "c1");
        var cred = credential("tok", "actor", rid, "tenant-1", Instant.now().plusSeconds(3600));
        when(ctx.getHeaderString("X-Worker-Credential")).thenReturn("tok");
        when(credentialStore.lookup("tok")).thenReturn(Optional.of(cred));
        when(currentPrincipal.tenancyId()).thenReturn("tenant-1");
        failFilter.filter(ctx);
        verify(ctx).abortWith(argThat(r -> r.getStatus() == 403));
    }

    private WorkerCredential credential(String token, String actorId,
            ResourceId resourceId, String tenancyId, Instant expiresAt) {
        return new WorkerCredential(token, actorId, resourceId, tenancyId,
            Set.of(new WorkerAction("READ_CONTEXT", AclAction.READ)),
            expiresAt, Instant.now());
    }
}
