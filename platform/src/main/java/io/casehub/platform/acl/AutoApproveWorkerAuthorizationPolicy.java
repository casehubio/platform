package io.casehub.platform.acl;

import io.casehub.platform.api.acl.WorkerAuthorizationPolicy;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

@DefaultBean
@ApplicationScoped
public class AutoApproveWorkerAuthorizationPolicy implements WorkerAuthorizationPolicy {
}
