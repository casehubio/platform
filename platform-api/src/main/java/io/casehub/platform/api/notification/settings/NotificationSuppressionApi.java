package io.casehub.platform.api.notification.settings;

import io.casehub.platform.api.mcp.HttpMethod;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestMethod;
import io.casehub.platform.api.mcp.RestPath;

import java.util.List;
import java.util.Optional;

@McpDomain("notification-suppression")
public interface NotificationSuppressionApi {

    @PlatformMutation("Add a mute rule")
    @RestPath("mute")
    MuteRule addMute(MuteRuleInput input);

    @PlatformQuery("List active mute rules")
    @RestPath("mute")
    List<MuteRule> listMutes();

    @PlatformMutation("Remove a mute rule")
    @RestMethod(HttpMethod.DELETE)
    @RestPath("mute")
    void removeMute(@PathParam String id);

    @PlatformMutation("Activate snooze")
    @RestPath("snooze")
    Snooze activateSnooze(SnoozeInput input);

    @PlatformQuery("Get active snooze")
    @RestPath("snooze")
    Optional<Snooze> getSnooze();

    @PlatformMutation("Cancel snooze")
    @RestMethod(HttpMethod.DELETE)
    @RestPath("snooze")
    void cancelSnooze();
}
