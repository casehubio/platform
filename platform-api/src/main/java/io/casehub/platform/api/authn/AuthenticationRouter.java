package io.casehub.platform.api.authn;

import java.util.Map;
import java.util.Set;

public interface AuthenticationRouter {
    ChallengeResponse initiate(AuthenticationContext context);
    AuthenticationResult verify(String method, String challengeId, Map<String, Object> data);
    Set<String> availableMethods();
}
