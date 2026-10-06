package io.casehub.platform.api.authn;

import java.util.Map;

public interface AuthenticationProvider {

    String method();

    ChallengeResponse initiate(AuthenticationContext context);

    AuthenticationResult verify(ChallengeRecord challenge, Map<String, Object> data);
}
