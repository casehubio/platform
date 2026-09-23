package io.casehub.platform.observability;

import io.casehub.platform.api.acl.AccessControlProvider;
import io.casehub.platform.api.acl.AclAction;
import io.casehub.platform.api.acl.AclEntryRequest;
import io.casehub.platform.api.acl.AclPage;
import io.casehub.platform.api.acl.AclQuery;
import io.casehub.platform.api.acl.ResourceId;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public class InstrumentedAccessControlProvider implements AccessControlProvider {

    private final AccessControlProvider delegate;
    private final MeterRegistry registry;

    public InstrumentedAccessControlProvider(AccessControlProvider delegate,
                                              MeterRegistry registry) {
        this.delegate = delegate;
        this.registry = registry;
    }

    @Override
    public boolean canAccess(String actorId, ResourceId resourceId, AclAction action) {
        registry.counter("casehub.platform.acl.can_access",
                "action", action.name()).increment();
        Timer.Sample sample = Timer.start(registry);
        try {
            return delegate.canAccess(actorId, resourceId, action);
        } finally {
            sample.stop(registry.timer("casehub.platform.acl.can_access.duration",
                    "action", action.name()));
        }
    }

    @Override public void grant(String a, ResourceId r, AclAction ac, Instant e) { delegate.grant(a, r, ac, e); }
    @Override public void grantBatch(Collection<AclEntryRequest> r) { delegate.grantBatch(r); }
    @Override public void revoke(String a, ResourceId r, AclAction ac) { delegate.revoke(a, r, ac); }
    @Override public void revokeBatch(Collection<AclEntryRequest> r) { delegate.revokeBatch(r); }
    @Override public void deny(String a, ResourceId r, AclAction ac, Instant e) { delegate.deny(a, r, ac, e); }
    @Override public void removeDeny(String a, ResourceId r, AclAction ac) { delegate.removeDeny(a, r, ac); }
    @Override public void denyBatch(Collection<AclEntryRequest> r) { delegate.denyBatch(r); }
    @Override public void removeDenyBatch(Collection<AclEntryRequest> r) { delegate.removeDenyBatch(r); }
    @Override public void revokeAll(String a, ResourceId r) { delegate.revokeAll(a, r); }
    @Override public void registerParent(ResourceId c, ResourceId p) { delegate.registerParent(c, p); }
    @Override public List<ResourceId> accessibleResources(String a, String t, AclAction ac) { return delegate.accessibleResources(a, t, ac); }
    @Override public AclPage accessibleResources(AclQuery q) { return delegate.accessibleResources(q); }
    @Override public List<ResourceId> accessibleResourcesIncludingInherited(String a, String t, AclAction ac) { return delegate.accessibleResourcesIncludingInherited(a, t, ac); }
}
