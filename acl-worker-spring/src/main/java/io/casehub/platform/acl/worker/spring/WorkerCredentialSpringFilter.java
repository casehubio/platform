package io.casehub.platform.acl.worker.spring;

import io.casehub.platform.acl.worker.ValidationResult;
import io.casehub.platform.acl.worker.WorkerCredentialValidator;
import io.casehub.platform.api.identity.CurrentPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Order(Integer.MIN_VALUE + 10)
public class WorkerCredentialSpringFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Worker-Credential";

    private final WorkerCredentialValidator validator;
    private final SpringWorkerScopeExtractor scopeExtractor;
    private final CurrentPrincipal currentPrincipal;

    public WorkerCredentialSpringFilter(
            WorkerCredentialValidator validator,
            SpringWorkerScopeExtractor scopeExtractor,
            CurrentPrincipal currentPrincipal) {
        this.validator = validator;
        this.scopeExtractor = scopeExtractor;
        this.currentPrincipal = currentPrincipal;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {
        String token = request.getHeader(HEADER);
        if (token == null) {
            chain.doFilter(request, response);
            return;
        }

        var requestResource = scopeExtractor.extractResourceId(request);
        var result = validator.validate(token, currentPrincipal.tenancyId(), requestResource);

        switch (result) {
            case ValidationResult.Rejected r -> {
                response.sendError(r.status(), r.message());
                return;
            }
            case ValidationResult.Accepted a ->
                request.setAttribute("workerCredential", a.credential());
        }

        chain.doFilter(request, response);
    }
}
