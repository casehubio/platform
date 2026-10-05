package io.casehub.platform.acl.admin;

import io.casehub.platform.api.acl.AccessControlProvider;
import io.casehub.platform.api.identity.CurrentPrincipal;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class AclAdminBeans {

    @Produces
    @ApplicationScoped
    public AclServiceCore aclServiceCore(AccessControlProvider acl, CurrentPrincipal principal) {
        return new AclServiceCore(acl, principal);
    }
}
