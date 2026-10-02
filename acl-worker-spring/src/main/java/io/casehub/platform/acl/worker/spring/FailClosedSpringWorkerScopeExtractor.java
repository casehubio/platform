package io.casehub.platform.acl.worker.spring;

import io.casehub.platform.api.acl.ResourceId;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Optional;

public class FailClosedSpringWorkerScopeExtractor implements SpringWorkerScopeExtractor {

    private static final ResourceId NEVER_MATCH =
        new ResourceId("__deny__", "__no_scope_extractor_configured__");

    @Override
    public Optional<ResourceId> extractResourceId(HttpServletRequest request) {
        return Optional.of(NEVER_MATCH);
    }
}
