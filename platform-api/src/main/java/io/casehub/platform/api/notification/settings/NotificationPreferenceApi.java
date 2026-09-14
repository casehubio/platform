package io.casehub.platform.api.notification.settings;

import io.casehub.platform.api.mcp.HttpMethod;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestMethod;

@McpDomain("notification-preferences")
public interface NotificationPreferenceApi {

    @PlatformQuery("Get notification preferences for the current user")
    NotificationPreferences get();

    @PlatformMutation("Update notification preferences for the current user")
    @RestMethod(HttpMethod.PUT)
    NotificationPreferences update(NotificationPreferenceUpdate update);
}
