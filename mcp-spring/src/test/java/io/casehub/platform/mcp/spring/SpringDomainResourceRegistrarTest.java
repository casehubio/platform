package io.casehub.platform.mcp.spring;

import io.casehub.platform.api.mcp.McpResourceHandle;
import io.casehub.platform.api.mcp.McpResourceRegistry;
import io.casehub.platform.api.mcp.McpResourceRegistration;
import io.casehub.platform.mcp.DomainModel;
import io.casehub.platform.mcp.DomainModelRegistry;
import io.casehub.platform.mcp.ModelScanComplete;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpringDomainResourceRegistrarTest {

    @Test
    void onScanComplete_registersDomainIndexAndTemplate() {
        DomainModelRegistry domainRegistry = new DomainModelRegistry();
        domainRegistry.register(new DomainModel("acl", "", "Access control",
                List.of(), List.of(), Map.of()));

        McpResourceRegistry resourceRegistry = mock(McpResourceRegistry.class);
        var mockRegistration = mock(McpResourceRegistration.class);
        when(resourceRegistry.newResource(any())).thenReturn(mockRegistration);
        when(mockRegistration.handler(any())).thenReturn(mockRegistration);
        when(mockRegistration.completion(any(), any())).thenReturn(mockRegistration);
        when(mockRegistration.register()).thenReturn(mock(McpResourceHandle.class));

        var registrar = new SpringDomainResourceRegistrar(resourceRegistry, domainRegistry);
        registrar.onScanComplete(new ModelScanComplete());

        verify(resourceRegistry, times(2)).newResource(any());
    }
}
