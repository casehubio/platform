package io.casehub.platform.api.notification;

import io.casehub.platform.api.mcp.HttpMethod;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestMethod;
import io.casehub.platform.api.mcp.RestPath;

import java.util.Map;
import java.util.Optional;

@McpDomain("notifications")
public interface NotificationApi {

    @PlatformQuery("List notifications for the current user")
    NotificationPage list(NotificationStatus status, String category, String cursor, Integer limit);

    @PlatformQuery("Get unread notification count")
    @RestPath("unread-count")
    Map<String, Long> unreadCount();

    @PlatformMutation("Mark a notification as read")
    @RestMethod(HttpMethod.PATCH)
    @RestPath("read")
    Optional<Notification> markRead(@PathParam String id);

    @PlatformMutation("Dismiss a notification")
    @RestMethod(HttpMethod.PATCH)
    @RestPath("dismiss")
    Optional<Notification> dismiss(@PathParam String id);

    @PlatformMutation("Mark all notifications as read")
    @RestPath("mark-all-read")
    Map<String, Integer> markAllRead();
}
