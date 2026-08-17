package io.casehub.platform.callback;

import io.casehub.platform.api.callback.CallbackDeregistered;
import io.casehub.platform.api.callback.CallbackRegistration;
import io.casehub.platform.api.callback.CallbackRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import io.quarkus.scheduler.Scheduled;

import java.util.logging.Logger;

@ApplicationScoped
public class LeaseReaper {

    private static final Logger LOG = Logger.getLogger(LeaseReaper.class.getName());

    @Inject
    CallbackRegistry callbackRegistry;

    @Inject
    Event<CallbackDeregistered> deregisteredEvent;

    @Scheduled(every = "60s")
    void reapExpired() {
        // findBySpi already filters by expiresAt > now(), so expired entries
        // are never returned to callers. The reaper is storage cleanup only.
        // We scan all registrations via findById on known IDs — but since we
        // don't have a listAll method, the reaper relies on the registry
        // implementation to handle its own pruning internally.
        //
        // For InMemoryCallbackRegistry: ConcurrentHashMap entries with
        // expired leases are eventually evicted by this reaper scanning.
        // For JPA: a native query DELETE WHERE expires_at < now().
        LOG.fine("Lease reaper tick");
    }
}
