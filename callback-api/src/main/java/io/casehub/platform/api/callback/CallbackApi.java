package io.casehub.platform.api.callback;

import io.casehub.platform.api.mcp.HttpMethod;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.RestMethod;

@McpDomain("callbacks")
public interface CallbackApi {

    @PlatformMutation("Register a callback")
    CallbackRegistration register(CallbackRegistrationRequest request);

    @PlatformMutation("Heartbeat a callback registration to extend its lease")
    @RestMethod(HttpMethod.PUT)
    void heartbeat(@PathParam String id);

    @PlatformMutation("Deregister a callback")
    @RestMethod(HttpMethod.DELETE)
    void deregister(@PathParam String id);
}
