package io.casehub.platform.identity;

import io.casehub.platform.api.identity.IdentityVerificationResult;
import io.quarkus.arc.DefaultBean;
import io.quarkus.arc.Unremovable;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Reactive bridge wrapping {@link AgentIdentityVerificationService}.
 *
 * <p>{@code @DefaultBean}: always active — pure bridge with no Hibernate Reactive dependency
 * (per reactive-spi-bridge-default-bean protocol).
 *
 * <p>{@code @Unremovable}: no injection point exists within the extension itself;
 * without this, ARC may dead-code-eliminate the bean before consumers inject it.
 */
@DefaultBean
@ApplicationScoped
@Unremovable
public class ReactiveAgentIdentityVerificationService {

    @Inject
    AgentIdentityVerificationService blockingService;

    /**
     * Reactive counterpart of {@link AgentIdentityVerificationService#verifyIdentityBinding}.
     * Offloads the blocking call to the Vert.x worker pool.
     */
    public Uni<IdentityVerificationResult> verifyIdentityBindingAsync(
            final String actorId,
            final String actorDid,
            final byte[] agentPublicKey) {
        return Uni.createFrom()
                .item(() -> blockingService.verifyIdentityBinding(actorId, actorDid, agentPublicKey))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }
}
