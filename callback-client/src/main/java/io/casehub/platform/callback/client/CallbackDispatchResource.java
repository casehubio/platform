package io.casehub.platform.callback.client;

import com.fasterxml.jackson.databind.JsonNode;
import io.casehub.platform.api.mcp.HeaderParam;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformWebhook;
import io.casehub.platform.api.mcp.RestPath;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

@McpDomain(value = "casehub/callbacks", basePath = "/casehub/callbacks")
@ApplicationScoped
public class CallbackDispatchResource {

    private final CallbackDispatcher dispatcher;

    @Inject
    public CallbackDispatchResource(CallbackDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @PlatformWebhook("Dispatch callback to SPI method")
    @RestPath("/{spiName}/{methodName}")
    public Response dispatch(@PathParam String spiName,
                             @PathParam String methodName,
                             @HeaderParam("X-CaseHub-SPI") String spiHeader,
                             JsonNode argsNode) {
        DispatchResult result = dispatcher.dispatch(spiName, methodName, spiHeader, argsNode);
        if (result.body() == null) {
            return Response.status(result.status()).build();
        }
        return Response.status(result.status()).entity(result.body()).build();
    }
}
