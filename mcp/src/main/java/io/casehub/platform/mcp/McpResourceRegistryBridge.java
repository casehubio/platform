package io.casehub.platform.mcp;

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
import io.quarkiverse.mcp.server.CompletionResponse;
import io.quarkiverse.mcp.server.ResourceManager;
import io.quarkiverse.mcp.server.ResourceResponse;
import io.quarkiverse.mcp.server.ResourceTemplateCompletionManager;
import io.quarkiverse.mcp.server.ResourceTemplateManager;
import io.quarkiverse.mcp.server.TextResourceContents;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

@ApplicationScoped
public class McpResourceRegistryBridge implements McpResourceRegistry {

    private static final Logger LOG = Logger.getLogger(McpResourceRegistryBridge.class);

    @Inject
    ResourceManager resourceManager;

    @Inject
    ResourceTemplateManager resourceTemplateManager;

    @Inject
    ResourceTemplateCompletionManager completionManager;

    @Inject
    Event<McpResourceRegistered> registeredEvent;

    @ConfigProperty(name = "casehub.mcp.server-name")
    Optional<String> serverName;

    private final ConcurrentMap<String, Registration> registrations = new ConcurrentHashMap<>();

    @Override
    public McpResourceRegistration newResource(McpResourceDescriptor descriptor) {
        return new BridgeRegistration(descriptor);
    }

    @Override
    public void deregister(String name) {
        Registration reg = registrations.remove(name);
        if (reg != null) {
            reg.invalidate();
            removeFromQuarkus(reg);
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

    void onResourceUpdated(@ObservesAsync McpResourceUpdated event) {
        for (Registration reg : registrations.values()) {
            if (reg.descriptor instanceof StaticResourceDescriptor s
                    && s.uri().equals(event.uri())
                    && reg.resourceInfo != null) {
                reg.resourceInfo.sendUpdateAndForget();
                return;
            }
        }
    }

    private void removeFromQuarkus(Registration reg) {
        switch (reg.descriptor) {
            case StaticResourceDescriptor s -> resourceManager.removeResource(s.uri());
            case TemplateResourceDescriptor t -> {
                resourceTemplateManager.removeResourceTemplate(t.name());
                completionManager.removeCompletion(info ->
                                                           t.name().equals(info.name()));
            }
        }
    }

    private record Registration(
            McpResourceDescriptor descriptor,
            ResourceManager.ResourceInfo resourceInfo,
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
        private String overrideServerName;

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
            return this;
        }

        @Override
        public McpResourceRegistration serverName(String serverName) {
            this.overrideServerName = serverName;
            return this;
        }

        @Override
        public McpResourceHandle register() {
            if (handler == null) {
                throw new IllegalStateException("handler is required — call .handler() before .register()");
            }

            String effectiveServerName = overrideServerName != null
                                         ? overrideServerName
                                         : serverName.orElse(null);
            AtomicBoolean valid = new AtomicBoolean(true);

            ResourceManager.ResourceInfo resourceInfo = switch (descriptor) {
                case StaticResourceDescriptor s -> registerStatic(s, effectiveServerName);
                case TemplateResourceDescriptor t -> {
                    if (t.subscribable()) {
                        throw new IllegalArgumentException(
                                "subscribable=true is not supported on template resources — "
                                + "quarkus-mcp-server 1.11.1 does not support resources/subscribe "
                                + "on template-resolved URIs");
                    }
                    registerTemplate(t, effectiveServerName);
                    yield null;
                }
            };

            Registration reg = new Registration(descriptor, resourceInfo, valid);
            registrations.put(descriptor.name(), reg);

            registeredEvent.fireAsync(new McpResourceRegistered(descriptor));

            LOG.infof("Registered MCP resource: %s (%s)",
                      descriptor.name(),
                      descriptor instanceof StaticResourceDescriptor ? "static" : "template");

            return new BridgeHandle(descriptor.name(), valid);
        }

        private ResourceManager.ResourceInfo registerStatic(StaticResourceDescriptor s,
                                                             String effectiveServerName) {
            var def = resourceManager.newResource(s.name())
                                     .setUri(s.uri())
                                     .setDescription(s.description())
                                     .setHandler(args -> {
                                         try {
                                             var    request = McpResourceReadRequest.of(args.requestUri().value());
                                             var    content = handler.read(request);
                                             String mime    = content.mimeType() != null ? content.mimeType() : s.mimeType();
                                             return new ResourceResponse(
                                                     new TextResourceContents(content.uri(), content.text(), mime));
                                         } catch (IllegalArgumentException e) {
                                             throw e;
                                         } catch (Exception e) {
                                             LOG.errorf(e, "MCP resource read failed: %s", s.uri());
                                             throw new RuntimeException(e.getMessage(), e);
                                         }
                                     }, true);

            if (s.mimeType() != null) {
                def.setMimeType(s.mimeType());
            }
            if (effectiveServerName != null) {
                def.setServerName(effectiveServerName);
            }

            return def.register();
        }

        private void registerTemplate(TemplateResourceDescriptor t, String effectiveServerName) {
            var def = resourceTemplateManager.newResourceTemplate(t.name())
                                             .setUriTemplate(t.uriTemplate())
                                             .setDescription(t.description())
                                             .setHandler(args -> {
                                                 try {
                                                     var request = new McpResourceReadRequest(
                                                             args.requestUri().value(), args.args());
                                                     var    content = handler.read(request);
                                                     String mime    = content.mimeType() != null ? content.mimeType() : t.mimeType();
                                                     return new ResourceResponse(
                                                             new TextResourceContents(content.uri(), content.text(), mime));
                                                 } catch (IllegalArgumentException e) {
                                                     throw e;
                                                 } catch (Exception e) {
                                                     LOG.errorf(e, "MCP resource template read failed: %s", t.uriTemplate());
                                                     throw new RuntimeException(e.getMessage(), e);
                                                 }
                                             }, true);

            if (t.mimeType() != null) {
                def.setMimeType(t.mimeType());
            }
            if (effectiveServerName != null) {
                def.setServerName(effectiveServerName);
            }

            def.register();

            for (var entry : completions.entrySet()) {
                var compDef = completionManager.newCompletion(t.name())
                                               .setArgumentName(entry.getKey())
                                               .setHandler(args -> {
                                                   List<String> values = new ArrayList<>(entry.getValue().get());
                                                   String       prefix = args.argumentValue();
                                                   if (prefix != null && !prefix.isEmpty()) {
                                                       values = values.stream()
                                                                      .filter(v -> v.startsWith(prefix))
                                                                      .toList();
                                                   }
                                                   return CompletionResponse.create(values);
                                               });
                if (effectiveServerName != null) {
                    compDef.setServerName(effectiveServerName);
                }
                compDef.register();
            }
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
            Registration reg = registrations.get(name);
            if (reg != null && reg.resourceInfo != null) {
                reg.resourceInfo.sendUpdateAndForget();
            }
        }

        @Override
        public void deregister() {
            if (!valid.compareAndSet(true, false)) return;
            McpResourceRegistryBridge.this.deregister(name);
        }
    }
}
