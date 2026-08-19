package io.casehub.platform.mcp;

import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.ModelEnricher;
import io.quarkus.arc.Arc;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class GraphQLModelScanner {

    private static final Logger LOG = Logger.getLogger(GraphQLModelScanner.class);

    @Inject
    ModelRegistry registry;

    @Inject
    @Any
    Instance<ModelEnricher> enrichers;
    @Inject
    Event<ModelScanComplete> scanComplete;


    void onStartup(@Observes StartupEvent event) {
        scan();
    }

    void scan() {
        Map<String, List<OperationDescriptor>> domainOps    = new LinkedHashMap<>();
        Map<String, List<EventDescriptor>>     domainEvents = new LinkedHashMap<>();

        var beans = Arc.container().beanManager()
                       .getBeans(Object.class, Any.Literal.INSTANCE);

        for (Bean<?> bean : beans) {
            Class<?>  beanClass = bean.getBeanClass();
            McpDomain mcpDomain = beanClass.getAnnotation(McpDomain.class);
            if (mcpDomain == null) {continue;}

            if (!hasGraphQLApi(beanClass)) {
                if (!ModelEnricher.class.isAssignableFrom(beanClass)) {
                    LOG.warnf("@McpDomain on %s without @GraphQLApi — skipping",
                              beanClass.getName());
                }
                continue;
            }

            String domain = mcpDomain.value();
            domainOps.computeIfAbsent(domain, k -> new ArrayList<>());
            domainEvents.computeIfAbsent(domain, k -> new ArrayList<>());

            for (Method method : beanClass.getDeclaredMethods()) {
                if (Modifier.isStatic(method.getModifiers())) {continue;}

                if (hasAnnotation(method, "org.eclipse.microprofile.graphql.Query")) {
                    domainOps.get(domain).add(buildOperation(method, beanClass,
                                                             OperationDescriptor.OperationType.QUERY));
                } else if (hasAnnotation(method, "org.eclipse.microprofile.graphql.Mutation")) {
                    domainOps.get(domain).add(buildOperation(method, beanClass,
                                                             OperationDescriptor.OperationType.MUTATION));
                } else if (hasAnnotation(method, "io.smallrye.graphql.api.Subscription")) {
                    domainEvents.get(domain).add(buildEvent(method, domain));
                }
            }
        }

        Map<String, ModelEnricher> enricherMap = resolveEnrichers();

        for (String domain : domainOps.keySet()) {
            ModelEnricher       enricher = enricherMap.get(domain);
            String              summary  = enricher != null ? enricher.summary() : "";
            Map<String, Object> state    = enricher != null ? enricher.state() : Map.of();

            DomainModel model = new DomainModel(domain, summary,
                                                List.copyOf(domainOps.get(domain)),
                                                List.copyOf(domainEvents.getOrDefault(domain, List.of())),
                                                state);
            registry.register(model);
            LOG.infof("MCP domain '%s': %d operations, %d events",
                      domain, model.operations().size(), model.events().size());
        }

        for (String enricherDomain : enricherMap.keySet()) {
            if (!domainOps.containsKey(enricherDomain)) {
                LOG.warnf("ModelEnricher for domain '%s' has no matching resolvers",
                          enricherDomain);
            }
        }

        scanComplete.fire(new ModelScanComplete());
    }

    private Map<String, ModelEnricher> resolveEnrichers() {
        Map<String, ModelEnricher> map = new HashMap<>();
        for (ModelEnricher enricher : enrichers) {
            McpDomain domainAnn = findMcpDomain(enricher.getClass());
            if (domainAnn != null) {
                map.put(domainAnn.value(), enricher);
            } else {
                LOG.warnf("ModelEnricher %s has no @McpDomain — ignoring",
                        enricher.getClass().getName());
            }
        }
        return map;
    }

    private McpDomain findMcpDomain(Class<?> cls) {
        while (cls != null && cls != Object.class) {
            McpDomain ann = cls.getAnnotation(McpDomain.class);
            if (ann != null) return ann;
            cls = cls.getSuperclass();
        }
        return null;
    }

    private boolean hasGraphQLApi(Class<?> cls) {
        return hasClassAnnotation(cls, "org.eclipse.microprofile.graphql.GraphQLApi");
    }

    private boolean hasClassAnnotation(Class<?> cls, String annotationName) {
        for (var ann : cls.getAnnotations()) {
            if (ann.annotationType().getName().equals(annotationName)) return true;
        }
        return false;
    }

    private boolean hasAnnotation(Method method, String annotationName) {
        for (var ann : method.getAnnotations()) {
            if (ann.annotationType().getName().equals(annotationName)) return true;
        }
        return false;
    }

    private OperationDescriptor buildOperation(Method method, Class<?> resolverClass,
                                                OperationDescriptor.OperationType type) {
        String summary = readDescription(method);
        List<ParameterDescriptor> params = buildParams(method);
        return new OperationDescriptor(method.getName(), type, summary, params,
                method.getReturnType().getSimpleName(), method, resolverClass);
    }

    private EventDescriptor buildEvent(Method method, String domain) {
        String summary = readDescription(method);
        String channel = domain + "-" + toKebabCase(method.getName());
        List<ParameterDescriptor> params = buildParams(method);
        return new EventDescriptor(method.getName(), summary, params, channel);
    }

    private String readDescription(Method method) {
        for (var ann : method.getAnnotations()) {
            if (ann.annotationType().getName()
                    .equals("org.eclipse.microprofile.graphql.Description")) {
                try {
                    return (String) ann.annotationType().getMethod("value").invoke(ann);
                } catch (Exception e) {
                    return "";
                }
            }
        }
        return "";
    }

    private String readParamName(Parameter param) {
        for (var ann : param.getAnnotations()) {
            if (ann.annotationType().getName()
                    .equals("org.eclipse.microprofile.graphql.Name")) {
                try {
                    return (String) ann.annotationType().getMethod("value").invoke(ann);
                } catch (Exception e) {
                    break;
                }
            }
        }
        return param.getName();
    }

    private String readParamDescription(Parameter param) {
        for (var ann : param.getAnnotations()) {
            if (ann.annotationType().getName()
                    .equals("org.eclipse.microprofile.graphql.Description")) {
                try {
                    return (String) ann.annotationType().getMethod("value").invoke(ann);
                } catch (Exception e) {
                    return "";
                }
            }
        }
        return "";
    }

    private List<ParameterDescriptor> buildParams(Method method) {
        List<ParameterDescriptor> params = new ArrayList<>();
        for (Parameter param : method.getParameters()) {
            String name = readParamName(param);
            String description = readParamDescription(param);
            Map<String, String> fields = expandFields(param.getType());
            boolean required = !Optional.class.isAssignableFrom(param.getType());

            params.add(new ParameterDescriptor(name, mapTypeName(param.getType()),
                    required, description, fields));
        }
        return params;
    }

    Map<String, String> expandFields(Class<?> type) {
        if (type.isPrimitive() || type == String.class || type == UUID.class
                || type == Instant.class || type.isEnum()
                || Map.class.isAssignableFrom(type)
                || List.class.isAssignableFrom(type)) {
            return Map.of();
        }
        Map<String, String> fields = new LinkedHashMap<>();
        if (type.isRecord()) {
            for (var component : type.getRecordComponents()) {
                fields.put(component.getName(), mapTypeName(component.getType()));
            }
        } else {
            for (var field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                fields.put(field.getName(), mapTypeName(field.getType()));
            }
        }
        return fields;
    }

    String mapTypeName(Class<?> type) {
        if (type == String.class) return "String";
        if (type == UUID.class) return "UUID";
        if (type == Instant.class) return "Instant";
        if (type == int.class || type == Integer.class) return "Integer";
        if (type == long.class || type == Long.class) return "Long";
        if (type == boolean.class || type == Boolean.class) return "Boolean";
        if (type == double.class || type == Double.class) return "Double";
        if (Map.class.isAssignableFrom(type)) return "JSON";
        if (List.class.isAssignableFrom(type)) return "List";
        return type.getSimpleName();
    }

    static String toKebabCase(String camelCase) {
        return camelCase.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase();
    }
}
