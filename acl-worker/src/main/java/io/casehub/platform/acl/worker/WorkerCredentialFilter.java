package io.casehub.platform.acl.worker;

import io.casehub.platform.api.acl.WorkerCredentialStore;
import io.casehub.platform.api.identity.CurrentPrincipal;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

@Provider
@Priority(Priorities.AUTHENTICATION - 10)
public class WorkerCredentialFilter implements ContainerRequestFilter {

    private static final Logger LOG = Logger.getLogger(WorkerCredentialFilter.class);
    private static final String HEADER = "X-Worker-Credential";

    private final WorkerCredentialStore credentialStore;
    private final WorkerScopeExtractor scopeExtractor;
    private final CurrentPrincipal currentPrincipal;

    @Inject
    public WorkerCredentialFilter(
            WorkerCredentialStore credentialStore,
            WorkerScopeExtractor scopeExtractor,
            CurrentPrincipal currentPrincipal) {
        this.credentialStore = credentialStore;
        this.scopeExtractor = scopeExtractor;
        this.currentPrincipal = currentPrincipal;
    }

    @Override
    public void filter(ContainerRequestContext ctx) {
        String token = ctx.getHeaderString(HEADER);
        if (token == null) {
            return;
        }

        var credential = credentialStore.lookup(token);
        if (credential.isEmpty()) {
            ctx.abortWith(Response.status(401).entity("Invalid worker credential").build());
            return;
        }

        var cred = credential.get();
        if (cred.isExpired()) {
            ctx.abortWith(Response.status(401).entity("Worker credential expired").build());
            return;
        }

        String requestTenancy = currentPrincipal.tenancyId();
        if (!cred.tenancyId().equals(requestTenancy)) {
            LOG.warnf("Worker credential tenancy violation: credential=%s request=%s",
                cred.tenancyId(), requestTenancy);
            ctx.abortWith(Response.status(403)
                .entity("Credential not scoped for this tenant").build());
            return;
        }

        var requestResource = scopeExtractor.extractResourceId(ctx);
        if (requestResource.isPresent()
                && !cred.resourceId().equals(requestResource.get())) {
            LOG.warnf("Worker credential scope violation: credential=%s request=%s",
                cred.resourceId(), requestResource.get());
            ctx.abortWith(Response.status(403)
                .entity("Credential not scoped for this resource").build());
            return;
        }

        ctx.setProperty("workerCredential", cred);
    }
}
