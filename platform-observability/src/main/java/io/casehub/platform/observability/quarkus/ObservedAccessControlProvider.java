package io.casehub.platform.observability.quarkus;

import io.casehub.platform.api.acl.AccessControlProvider;
import io.casehub.platform.api.acl.AclAction;
import io.casehub.platform.api.acl.AclEntryRequest;
import io.casehub.platform.api.acl.AclPage;
import io.casehub.platform.api.acl.AclQuery;
import io.casehub.platform.api.acl.ResourceId;
import io.casehub.platform.observability.InstrumentedAccessControlProvider;
import io.micrometer.core.instrument.MeterRegistry;

import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Decorator
@Priority(1900)
public class ObservedAccessControlProvider implements AccessControlProvider {

    private final InstrumentedAccessControlProvider instrumented;

    @Inject
    ObservedAccessControlProvider(@Delegate AccessControlProvider delegate, MeterRegistry registry) {
        this.instrumented = new InstrumentedAccessControlProvider(delegate, registry);
    }

    @Override public boolean canAccess(String a, ResourceId r, AclAction ac) { return instrumented.canAccess(a, r, ac); }
    @Override public void grant(String a, ResourceId r, AclAction ac, Instant e) { instrumented.grant(a, r, ac, e); }
    @Override public void grantBatch(Collection<AclEntryRequest> r) { instrumented.grantBatch(r); }
    @Override public void revoke(String a, ResourceId r, AclAction ac) { instrumented.revoke(a, r, ac); }
    @Override public void revokeBatch(Collection<AclEntryRequest> r) { instrumented.revokeBatch(r); }
    @Override public void deny(String a, ResourceId r, AclAction ac, Instant e) { instrumented.deny(a, r, ac, e); }
    @Override public void removeDeny(String a, ResourceId r, AclAction ac) { instrumented.removeDeny(a, r, ac); }
    @Override public void denyBatch(Collection<AclEntryRequest> r) { instrumented.denyBatch(r); }
    @Override public void removeDenyBatch(Collection<AclEntryRequest> r) { instrumented.removeDenyBatch(r); }
    @Override public void revokeAll(String a, ResourceId r) { instrumented.revokeAll(a, r); }
    @Override public void registerParent(ResourceId c, ResourceId p) { instrumented.registerParent(c, p); }
    @Override public List<ResourceId> accessibleResources(String a, String t, AclAction ac) { return instrumented.accessibleResources(a, t, ac); }
    @Override public AclPage accessibleResources(AclQuery q) { return instrumented.accessibleResources(q); }
    @Override public List<ResourceId> accessibleResourcesIncludingInherited(String a, String t, AclAction ac) { return instrumented.accessibleResourcesIncludingInherited(a, t, ac); }
}
