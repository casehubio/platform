package io.casehub.platform.compiler;

import io.serverlessworkflow.api.types.CallHTTP;
import io.serverlessworkflow.api.types.CallTask;
import io.serverlessworkflow.api.types.Endpoint;
import io.serverlessworkflow.api.types.HTTPArguments;
import io.serverlessworkflow.api.types.Task;
import io.serverlessworkflow.api.types.TaskItem;
import io.serverlessworkflow.api.types.UriTemplate;
import jakarta.validation.ConstraintViolationException;

import java.net.URI;
import java.util.Map;

class RestRequestCompiler implements StepCompiler {

    @Override
    @SuppressWarnings("unchecked")
    public TaskItem compile(String stepKey, Object stepValue, Map<String, Object> fullStep, CompilationContext ctx) {
        String              name = ctx.generateName("call");
        Map<String, Object> config;
        if (stepValue instanceof String s) {
            config = Map.of("url", s, "method", "GET");
        } else if (stepValue instanceof Map) {
            config = new java.util.LinkedHashMap<>((Map<String, Object>) stepValue);
        } else {
            return null;
        }

        if (config.containsKey("_subject")) {
            String subject = String.valueOf(config.get("_subject"));
            config.remove("_subject");
            int spaceIdx = subject.indexOf(' ');
            if (spaceIdx > 0) {
                config.putIfAbsent("method", subject.substring(0, spaceIdx));
                config.putIfAbsent("url", subject.substring(spaceIdx + 1));
            } else {
                config.putIfAbsent("method", subject);
            }
        }
        if (config.containsKey("_value")) {
            config.putIfAbsent("url", String.valueOf(config.get("_value")));
            config.remove("_value");
        }

        String method = String.valueOf(config.getOrDefault("method", "GET"));
        String url    = String.valueOf(config.get("url"));

        Endpoint endpoint;
        if (url.matches("^\\s*\\$\\{.+}\\s*$")) {
            endpoint = new Endpoint().withRuntimeExpression(url.strip());
        } else {
            UriTemplate uriTemplate;
            try {
                uriTemplate = new UriTemplate().withLiteralUriTemplate(url);
            } catch (IllegalArgumentException | ConstraintViolationException e) {
                uriTemplate = new UriTemplate().withLiteralUri(URI.create(url));
            }
            endpoint = new Endpoint().withUriTemplate(uriTemplate);
        }
        var httpArgs = new HTTPArguments(method, endpoint);

        if (config.containsKey("body")) {
            httpArgs.withBody(config.get("body"));
        }

        var callHttp = new CallHTTP().withCall("http").withWith(httpArgs);
        return new TaskItem(name, new Task().withCallTask(new CallTask().withCallHTTP(callHttp)));
    }
}
