package io.casehub.platform.acl.worker.spring;

import io.casehub.platform.api.acl.ResourceId;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Optional;

public interface SpringWorkerScopeExtractor {
    Optional<ResourceId> extractResourceId(HttpServletRequest request);
}
