package io.casehub.platform.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentResolver;
import io.casehub.schema.generator.PlatformSchemaGenerator;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.lang.reflect.Constructor;

@ApplicationScoped
public class AgentServiceBeans {

    private AgentProvider agentProvider;
    private AgentResolver agentResolver;
    private PlatformSchemaGenerator schemaGenerator;
    private ObjectMapper objectMapper;

    /** Required by CDI for the client proxy of this normal-scoped bean. */
    protected AgentServiceBeans() {}

    /**
     * CDI constructor. Constructor injection (not field injection) keeps Spring, which also honours
     * {@code @Inject} on fields of {@code @Bean} instances, from trying to resolve {@link Instance}.
     */
    @Inject
    public AgentServiceBeans(AgentProvider agentProvider, AgentResolver agentResolver,
                             Instance<ObjectMapper> objectMapperInstance) {
        this(agentProvider, agentResolver,
             objectMapperInstance.isResolvable() ? objectMapperInstance.get() : null);
    }

    public AgentServiceBeans(AgentProvider agentProvider, AgentResolver agentResolver) {
        this(agentProvider, agentResolver, (ObjectMapper) null);
    }

    /**
     * @param objectMapper mapper for parsing structured agent responses; {@code null} uses a default
     *                     with all discoverable Jackson modules registered
     */
    public AgentServiceBeans(AgentProvider agentProvider, AgentResolver agentResolver, ObjectMapper objectMapper) {
        this.agentProvider = agentProvider;
        this.agentResolver = agentResolver;
        this.objectMapper = objectMapper != null ? objectMapper : defaultObjectMapper();
        this.schemaGenerator = new PlatformSchemaGenerator();
    }

    public <T> T createProxy(Class<T> serviceInterface) {
        String proxyClassName = serviceInterface.getName() + "Proxy";
        try {
            @SuppressWarnings("unchecked")
            Class<T> proxyClass = (Class<T>) serviceInterface.getClassLoader().loadClass(proxyClassName);
            Constructor<T> constructor = proxyClass.getConstructor(
                    AgentProvider.class, AgentResolver.class, PlatformSchemaGenerator.class, ObjectMapper.class);
            return constructor.newInstance(agentProvider, agentResolver, schemaGenerator, objectMapper);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                "Failed to create proxy for @AgentService interface " + serviceInterface.getName()
                + ". Ensure agent-service-generator is on the annotation processor path.", e);
        }
    }

    private static ObjectMapper defaultObjectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }
}
