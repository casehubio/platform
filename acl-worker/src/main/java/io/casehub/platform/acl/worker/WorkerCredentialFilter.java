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

@Provider
@Priority(Priorities.AUTHENTICATION - 10)
public class WorkerCredentialFilter implements ContainerRequestFilter {

    private static final String HEADER = "X-Worker-Credential";

    private final WorkerCredentialValidator validator;
    private final WorkerScopeExtractor scopeExtractor;
    private final CurrentPrincipal currentPrincipal;

    @Inject
    public WorkerCredentialFilter(
            WorkerCredentialStore credentialStore,
            WorkerScopeExtractor scopeExtractor,
            CurrentPrincipal currentPrincipal) {
        this.validator = new WorkerCredentialValidator(credentialStore);
        this.scopeExtractor = scopeExtractor;
        this.currentPrincipal = currentPrincipal;
    }

    @Override
    public void filter(ContainerRequestContext ctx) {
        String token = ctx.getHeaderString(HEADER);
        if (token == null) {
            return;
        }

        var requestResource = scopeExtractor.extractResourceId(ctx);
        var result = validator.validate(token, currentPrincipal.tenancyId(), requestResource);

        switch (result) {
            case ValidationResult.Rejected r ->
                ctx.abortWith(Response.status(r.status()).entity(r.message()).build());
            case ValidationResult.Accepted a ->
                ctx.setProperty("workerCredential", a.credential());
        }
    }
}
