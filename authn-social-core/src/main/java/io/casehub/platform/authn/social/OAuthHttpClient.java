package io.casehub.platform.authn.social;

import java.util.Map;

public interface OAuthHttpClient {
    OAuthTokenResponse exchangeCode(String tokenEndpoint, Map<String, String> params);
    Map<String, Object> fetchUserInfo(String userInfoEndpoint, String accessToken);
}
