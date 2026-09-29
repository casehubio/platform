package io.casehub.platform.mcp.spring;

import io.casehub.platform.api.mcp.McpResourceContent;
import io.casehub.platform.api.mcp.McpResourceDescriptor;
import io.casehub.platform.api.mcp.McpResourceHandle;
import io.casehub.platform.api.mcp.McpResourceHandler;
import io.casehub.platform.api.mcp.McpResourceReadRequest;
import io.casehub.platform.api.mcp.McpResourceRegistered;
import io.casehub.platform.api.mcp.McpResourceRegistration;
import io.casehub.platform.api.mcp.McpResourceRegistry;
import io.casehub.platform.api.mcp.McpResourceUpdated;
import io.casehub.platform.api.mcp.StaticResourceDescriptor;
import io.casehub.platform.api.mcp.TemplateResourceDescriptor;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public class SpringMcpResourceRegistryBridge implements McpResourceRegistry {

    private static final System.Logger LOG = System.getLogger(SpringMcpResourceRegistryBridge.class.getName());

    private final McpSyncServer server;
    private final ApplicationEventPublisher eventPublisher;
    private final ConcurrentMap<String, Registration> registrations = new ConcurrentHashMap<>();

    public SpringMcpResourceRegistryBridge(McpSyncServer server,
                                            ApplicationEventPublisher eventPublisher) {
        this.server = server;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public McpResourceRegistration newResource(McpResourceDescriptor descriptor) {
        return new BridgeRegistration(descriptor);
    }

    @Override
    public void deregister(String name) {
        Registration reg = registrations.remove(name);
        if (reg != null) {
            reg.invalidate();
            removeFromServer(reg);
        }
    }

    @Override
    public Optional<McpResourceDescriptor> resolve(String name) {
        Registration reg = registrations.get(name);
        return reg != null ? Optional.of(reg.descriptor) : Optional.empty();
    }

    @Override
    public List<McpResourceDescriptor> list() {
        return registrations.values().stream()
                .map(r -> r.descriptor)
                .toList();
    }

    @EventListener
    void onResourceUpdated(McpResourceUpdated event) {
        for (Registration reg : registrations.values()) {
            if (reg.descriptor instanceof StaticResourceDescriptor s
                    && s.uri().equals(event.uri())) {
                try {
                    server.notifyResourcesUpdated(
                            new McpSchema.ResourcesUpdatedNotification(event.uri(), null));
                } catch (Exception e) {
                    LOG.log(System.Logger.Level.WARNING,
                            "Failed to notify resource update: {0}", event.uri());
                }
                return;
            }
        }
    }

    private void removeFromServer(Registration reg) {
        try {
            switch (reg.descriptor) {
                case StaticResourceDescriptor s -> server.removeResource(s.uri());
                case TemplateResourceDescriptor t -> server.removeResourceTemplate(t.uriTemplate());
            }
        } catch (Exception e) {
            LOG.log(System.Logger.Level.WARNING,
                    "Failed to remove MCP resource from server: {0}", reg.descriptor.name());
        }
    }

    private record Registration(
            McpResourceDescriptor descriptor,
            AtomicBoolean valid
    ) {
        void invalidate() {
            valid.set(false);
        }
    }

    private class BridgeRegistration implements McpResourceRegistration {

        private final McpResourceDescriptor descriptor;
        private McpResourceHandler handler;
        private final Map<String, Supplier<List<String>>> completions = new LinkedHashMap<>();

        BridgeRegistration(McpResourceDescriptor descriptor) {
            this.descriptor = descriptor;
        }

        @Override
        public McpResourceRegistration handler(McpResourceHandler handler) {
            this.handler = handler;
            return this;
        }

        @Override
        public McpResourceRegistration completion(String argumentName, Supplier<List<String>> values) {
            completions.put(argumentName, values);
            LOG.log(System.Logger.Level.DEBUG,
                    "Completion for ''{0}'' stored but not forwarded — MCP SDK 2.x lacks runtime completion registration",
                    argumentName);
            return this;
        }

        @Override
        public McpResourceRegistration serverName(String serverName) {
            // Spring AI MCP Server does not support multi-server scoping — ignored
            return this;
        }

        @Override
        public McpResourceHandle register() {
            if (handler == null) {
                throw new IllegalStateException("handler is required — call .handler() before .register()");
            }

            AtomicBoolean valid = new AtomicBoolean(true);

            switch (descriptor) {
                case StaticResourceDescriptor s -> registerStatic(s);
                case TemplateResourceDescriptor t -> {
                    if (t.subscribable()) {
                        throw new IllegalArgumentException(
                                "subscribable=true is not supported on template resources");
                    }
                    registerTemplate(t);
                }
            }

            registrations.put(descriptor.name(), new Registration(descriptor, valid));
            eventPublisher.publishEvent(new McpResourceRegistered(descriptor));

            LOG.log(System.Logger.Level.INFO, "Registered MCP resource: {0} ({1})",
                    descriptor.name(),
                    descriptor instanceof StaticResourceDescriptor ? "static" : "template");

            return new BridgeHandle(descriptor.name(), valid);
        }

        private void registerStatic(StaticResourceDescriptor s) {
            var resource = new McpSchema.Resource(s.uri(), s.name(), null,
                    s.description(), s.mimeType(), null, null, null, null);
            server.addResource(new McpServerFeatures.SyncResourceSpecification(
                    resource,
                    (exchange, request) -> {
                        try {
                            var readRequest = McpResourceReadRequest.of(request.uri());
                            var content = handler.read(readRequest);
                            String mime = content.mimeType() != null ? content.mimeType() : s.mimeType();
                            return new McpSchema.ReadResourceResult(List.of(
                                    new McpSchema.TextResourceContents(
                                            content.uri(), mime, content.text(), null)), null);
                        } catch (IllegalArgumentException e) {
                            throw e;
                        } catch (Exception e) {
                            LOG.log(System.Logger.Level.ERROR, "MCP resource read failed: {0}", s.uri());
                            throw new RuntimeException(e.getMessage(), e);
                        }
                    }
            ));
        }

        private void registerTemplate(TemplateResourceDescriptor t) {
            var template = new McpSchema.ResourceTemplate(t.uriTemplate(), t.name(), null,
                    t.description(), t.mimeType(), null, null, null);
            server.addResourceTemplate(new McpServerFeatures.SyncResourceTemplateSpecification(
                    template,
                    (exchange, request) -> {
                        try {
                            var readRequest = new McpResourceReadRequest(request.uri(), Map.of());
                            var content = handler.read(readRequest);
                            String mime = content.mimeType() != null ? content.mimeType() : t.mimeType();
                            return new McpSchema.ReadResourceResult(List.of(
                                    new McpSchema.TextResourceContents(
                                            content.uri(), mime, content.text(), null)), null);
                        } catch (IllegalArgumentException e) {
                            throw e;
                        } catch (Exception e) {
                            LOG.log(System.Logger.Level.ERROR, "MCP resource template read failed: {0}", t.uriTemplate());
                            throw new RuntimeException(e.getMessage(), e);
                        }
                    }
            ));
        }
    }

    private class BridgeHandle implements McpResourceHandle {

        private final String name;
        private final AtomicBoolean valid;

        BridgeHandle(String name, AtomicBoolean valid) {
            this.name = name;
            this.valid = valid;
        }

        @Override
        public void notifyUpdate(String uri) {
            if (!valid.get()) return;
            try {
                server.notifyResourcesUpdated(
                        new McpSchema.ResourcesUpdatedNotification(uri, null));
            } catch (Exception e) {
                LOG.log(System.Logger.Level.WARNING,
                        "Failed to notify resource update: {0}", uri);
            }
        }

        @Override
        public void deregister() {
            if (!valid.compareAndSet(true, false)) return;
            SpringMcpResourceRegistryBridge.this.deregister(name);
        }
    }
}
