package io.casehub.yaml.step.handler;

import io.casehub.yaml.core.step.InvokeBinding;
import io.casehub.yaml.core.step.Declaration;
import io.casehub.yaml.plugin.api.Action;
import io.casehub.yaml.plugin.api.Result;
import io.casehub.yaml.step.InvokeHandler;

import java.util.LinkedHashMap;
import java.util.Map;

public class McpInvokeHandler implements InvokeHandler {

    @Override
    public boolean supports(InvokeBinding binding) {
        return binding instanceof InvokeBinding.Mcp;
    }

    @Override
    public Action create(Declaration declaration, InvokeBinding binding) {
        InvokeBinding.Mcp mcp = (InvokeBinding.Mcp) binding;
        return (params, services) -> {
            Map<String, Object> output = new LinkedHashMap<>();
            if (services != null) {
                output.put("delegated", true);
            }
            return Result.of(output, Map.of("tool", mcp.tool()));
        };
    }
}
