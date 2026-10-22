package io.casehub.platform.compiler;

import org.openworkflow.sdk.api.types.CallHTTP;
import org.openworkflow.sdk.api.types.CallTask;
import org.openworkflow.sdk.api.types.Endpoint;
import org.openworkflow.sdk.api.types.HTTPArguments;
import org.openworkflow.sdk.api.types.Task;
import org.openworkflow.sdk.api.types.TaskItem;
import org.openworkflow.sdk.api.types.UriTemplate;

import java.net.URI;
import java.util.Map;

class RestRequestCompiler implements StepCompiler {

    @Override
    @SuppressWarnings("unchecked")
    public TaskItem compile(String stepKey, Object stepValue, Map<String, Object> fullStep, CompilationContext ctx) {
        String name = ctx.generateName("call");
        Map<String, Object> config = (Map<String, Object>) stepValue;

        String method = String.valueOf(config.getOrDefault("method", "GET"));
        String url = String.valueOf(config.get("url"));

        var uriTemplate = new UriTemplate().withLiteralUri(URI.create(url));
        var endpoint = new Endpoint().withUriTemplate(uriTemplate);
        var httpArgs = new HTTPArguments(method, endpoint);

        if (config.containsKey("body")) {
            httpArgs.withBody(config.get("body"));
        }

        var callHttp = new CallHTTP().withCall("http").withWith(httpArgs);
        return new TaskItem(name, new Task().withCallTask(new CallTask().withCallHTTP(callHttp)));
    }
}
