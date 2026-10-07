package io.casehub.platform.api.authn;

import java.util.Set;

public interface IncrementalConsentHandler {
    ConsentRequest requestAdditionalScopes(
        String actorId, String provider, String tenancyId,
        Set<String> missingScopes);
}
