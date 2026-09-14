package io.casehub.platform.api.delivery;

import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PlatformQuery;

import java.util.Set;

@McpDomain("delivery-channels")
public interface DeliveryChannelApi {

    @PlatformQuery("List all registered delivery channels")
    Set<DeliveryChannelDescriptor> listChannels();
}
