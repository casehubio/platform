package io.casehub.platform.authn;

import io.casehub.platform.api.authn.ConsentRequest;
import io.casehub.platform.api.authn.IncrementalConsentHandler;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Set;

@DefaultBean
@ApplicationScoped
public class NoOpIncrementalConsentHandler implements IncrementalConsentHandler {

    @Override
    public ConsentRequest requestAdditionalScopes(
            String actorId, String provider, String tenancyId,
            Set<String> missingScopes) {
        throw new UnsupportedOperationException(
                "Incremental consent requires authn-social modules on classpath");
    }
}
