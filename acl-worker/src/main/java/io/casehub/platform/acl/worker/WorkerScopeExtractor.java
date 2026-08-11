package io.casehub.platform.acl.worker;

import io.casehub.platform.api.acl.ResourceId;
import jakarta.ws.rs.container.ContainerRequestContext;
import java.util.Optional;

public interface WorkerScopeExtractor {
    Optional<ResourceId> extractResourceId(ContainerRequestContext ctx);
}
