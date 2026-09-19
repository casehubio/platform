package io.casehub.platform.scim;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.rest.client.inject.RestClient;

public class ScimBeans {

    @Produces
    @ApplicationScoped
    public ScimGroupMembershipProviderCore scimGroupMembershipProviderCore(
            @RestClient QuarkusScimClient scimClient,
            ScimConfig config) {
        return new ScimGroupMembershipProviderCore(scimClient, config);
    }
}
