package io.casehub.platform.simulation.strategy;

import io.casehub.platform.simulation.KeyExtractor;
import io.casehub.platform.simulation.RestInvocation;

public class RestClientKeyExtractor implements KeyExtractor<RestInvocation> {

    @Override
    public String extract(final RestInvocation invocation) {
        String path = invocation.pathTemplate();
        for (var entry : invocation.params().entrySet()) {
            path = path.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }
        return invocation.httpMethod() + " " + path;
    }
}
