package io.casehub.platform.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.inject.Inject;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class ReflectiveOperationDispatcher implements io.casehub.platform.api.mcp.ToolDispatcher {

    @Inject
    DomainModelRegistry registry;

    private final ObjectMapper mapper;

    public ReflectiveOperationDispatcher() {
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
    }

    public Object dispatch(String domain, String operation, Map<String, Object> params)
            throws Exception {
        OperationDescriptor op = registry.getOperation(domain, operation)
                                         .orElseThrow(() -> new IllegalArgumentException(
                                                 "Unknown operation: " + domain + "." + operation));

        Map<String, Object> effectiveParams = params != null ? params : Map.of();
        validateParams(op, effectiveParams);

        Object resolverBean = CDI.current().select(op.resolverClass()).get();

        Method      method       = op.method();
        Parameter[] methodParams = method.getParameters();
        Object[]    args         = new Object[methodParams.length];

        for (int i = 0; i < methodParams.length; i++) {
            Parameter param     = methodParams[i];
            String    paramName = resolveParamName(param);

            Object rawValue = effectiveParams.get(paramName);
            if (rawValue != null) {
                args[i] = mapper.convertValue(rawValue, param.getType());
            }
        }

        try {
            return method.invoke(resolverBean, args);
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof io.casehub.platform.api.mcp.McpCapabilityException capEx) {
                return io.casehub.platform.api.mcp.McpOperationResult.from(capEx, domain, operation);
            }
            io.casehub.platform.api.mcp.McpOperationResult capResult =
                    tryExtractCapabilityResult(cause, domain, operation);
            if (capResult != null) {
                return capResult;
            }
            if (cause instanceof RuntimeException re) {throw re;}
            if (cause instanceof Exception ex) {throw ex;}
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    private io.casehub.platform.api.mcp.McpOperationResult tryExtractCapabilityResult(
            Throwable cause, String domain, String operation) {
        try {
            String capability = (String) cause.getClass().getMethod("capability").invoke(cause);
            String provider   = (String) cause.getClass().getMethod("provider").invoke(cause);
            List<String> supported = (List<String>) cause.getClass()
                                                         .getMethod("supportedCapabilities").invoke(cause);
            return new io.casehub.platform.api.mcp.McpOperationResult(
                    io.casehub.platform.api.mcp.OperationOutcome.UNSUPPORTED,
                    domain, operation, capability, provider,
                    supported != null ? supported : List.of(), cause.getMessage());
        } catch (NoSuchMethodException e) {
            return null;
        } catch (Exception e) {
            return null;
        }
    }


    private void validateParams(OperationDescriptor op, Map<String, Object> params) {
        List<String> errors = new ArrayList<>();

        Set<String> knownNames = op.params().stream()
                                   .map(ParameterDescriptor::name)
                                   .collect(Collectors.toSet());

        for (String provided : params.keySet()) {
            if (!knownNames.contains(provided)) {
                errors.add("Unknown parameter '" + provided + "'");
            }
        }

        for (ParameterDescriptor expected : op.params()) {
            if (expected.required() && !params.containsKey(expected.name())) {
                errors.add("Required parameter '" + expected.name()
                           + "' (type: " + expected.typeName() + ") is missing");
            }
        }

        if (!errors.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append("Invalid params for ").append(op.name()).append(": ");
            sb.append(String.join("; ", errors));
            sb.append(". Expected: ");
            sb.append(op.params().stream()
                        .map(p -> p.name() + ": " + p.typeName()
                                  + (p.required() ? " (required)" : " (optional)")
                                  + (p.fields().isEmpty() ? "" : " " + p.fields()))
                        .collect(Collectors.joining(", ")));
            throw new IllegalArgumentException(sb.toString());
        }
    }


    private String resolveParamName(Parameter param) {
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
}
