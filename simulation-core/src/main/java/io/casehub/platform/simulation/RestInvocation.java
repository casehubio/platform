package io.casehub.platform.simulation;

import java.util.Map;

public record RestInvocation(
        String spiName,
        String methodName,
        String httpMethod,
        String pathTemplate,
        Map<String, Object> params,
        Object body
) {}
