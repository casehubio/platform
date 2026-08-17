package io.casehub.platform.callback.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jboss.logging.Logger;

/**
 * Receives callback invocations from a CaseHub server's {@code CallbackInvoker} and routes
 * them to local CDI beans implementing the targeted SPI.
 *
 * <p>SPI beans register themselves via {@link #registerSpi(String, Object)} at startup
 * (called by {@link CallbackAutoRegistrar}).
 */
@Path("/casehub/callbacks")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CallbackDispatchResource {

    private static final Logger LOG = Logger.getLogger(CallbackDispatchResource.class);

    private final Map<String, Object> spiRegistry = new ConcurrentHashMap<>();

    @Inject
    ObjectMapper objectMapper;

    void registerSpi(final String spiName, final Object bean) {
        spiRegistry.put(spiName, bean);
    }

    @POST
    @Path("/{spiName}/{methodName}")
    public Response dispatch(@PathParam("spiName") final String spiName,
                             @PathParam("methodName") final String methodName,
                             @HeaderParam("X-CaseHub-SPI") final String spiHeader,
                             final JsonNode argsNode) {
        if (spiHeader == null || spiHeader.isBlank()) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(Map.of("error", "Missing X-CaseHub-SPI header"))
                    .build();
        }

        final Object bean = spiRegistry.get(spiName);
        if (bean == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "No SPI registered for: " + spiName))
                    .build();
        }

        try {
            final int argCount = (argsNode != null && argsNode.isArray()) ? argsNode.size() : 0;
            final Method method = findMethod(bean.getClass(), methodName, argCount);
            if (method == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "No method '" + methodName + "' with " + argCount + " args on SPI " + spiName))
                        .build();
            }

            final Object[] args = deserializeArgs(argsNode, method);
            method.setAccessible(true);
            final Object result = method.invoke(bean, args);

            if (method.getReturnType() == void.class) {
                return Response.noContent().build();
            }
            return Response.ok(result).build();
        } catch (final InvocationTargetException e) {
            LOG.errorf(e.getCause(), "SPI method %s.%s threw", spiName, methodName);
            return Response.serverError()
                    .entity(Map.of("error", e.getCause().getMessage()))
                    .build();
        } catch (final Exception e) {
            LOG.errorf(e, "Dispatch failed for %s.%s", spiName, methodName);
            return Response.serverError()
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    private Method findMethod(final Class<?> clazz, final String name, final int argCount) {
        for (final Method m : clazz.getMethods()) {
            if (m.getName().equals(name) && !m.isSynthetic()
                    && m.getParameterCount() == argCount) {
                return m;
            }
        }
        return null;
    }

    private Object[] deserializeArgs(final JsonNode argsNode, final Method method) throws Exception {
        final Class<?>[] paramTypes = method.getParameterTypes();
        final Object[] args = new Object[paramTypes.length];

        if (argsNode == null || argsNode.isNull() || !argsNode.isArray()) {
            return args;
        }

        for (int i = 0; i < paramTypes.length && i < argsNode.size(); i++) {
            args[i] = objectMapper.treeToValue(argsNode.get(i), paramTypes[i]);
        }
        return args;
    }
}
