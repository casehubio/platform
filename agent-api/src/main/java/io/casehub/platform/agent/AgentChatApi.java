package io.casehub.platform.agent;

import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;

import java.util.List;

@McpDomain("agent-chat")
public interface AgentChatApi {

    @PlatformQuery("List available agents")
    List<AgentDescriptor> listAgents();

    @PlatformMutation("Invoke an agent")
    AgentChatResponse invoke(AgentChatRequest request);
}
