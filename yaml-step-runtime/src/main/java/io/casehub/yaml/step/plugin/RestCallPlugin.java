package io.casehub.yaml.step.plugin;

import io.casehub.yaml.plugin.api.Execute;
import io.casehub.yaml.plugin.api.Optional;
import io.casehub.yaml.plugin.api.Required;
import io.casehub.yaml.plugin.api.StepPlugin;
import io.casehub.yaml.plugin.api.StepResult;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@StepPlugin(value = "rest-call", description = "Makes an HTTP request")
public record RestCallPlugin(
        @Required String url,
        @Optional String method,
        @Optional Map<String, Object> body,
        @Optional Map<String, String> headers,
        @Optional String timeout) {

    public RestCallPlugin {
        if (method == null) method = "GET";
    }

    @Execute
    public StepResult run() {
        try {
            HttpClient client = HttpClient.newHttpClient();

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url));

            if (timeout != null) {
                reqBuilder.timeout(parseTimeout(timeout));
            }

            if (headers != null) {
                headers.forEach(reqBuilder::header);
            }

            String bodyStr = body != null ? serializeBody(body) : null;

            reqBuilder.method(method.toUpperCase(java.util.Locale.ROOT),
                    bodyStr != null
                            ? HttpRequest.BodyPublishers.ofString(bodyStr)
                            : HttpRequest.BodyPublishers.noBody());

            HttpResponse<String> response = client.send(reqBuilder.build(),
                    HttpResponse.BodyHandlers.ofString());

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("status-code", response.statusCode());
            output.put("body", response.body());

            if (response.statusCode() >= 400) {
                return StepResult.failed("HTTP " + response.statusCode()
                        + (response.body() != null ? ": " + response.body() : ""));
            }

            return StepResult.of(output);
        } catch (Exception e) {
            return StepResult.failed("HTTP request failed: " + e.getMessage());
        }
    }

    private static String serializeBody(Map<String, Object> body) {
        var sb = new StringBuilder("{");
        var it = body.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            sb.append("\"").append(entry.getKey()).append("\":");
            Object val = entry.getValue();
            if (val instanceof String) {
                sb.append("\"").append(val).append("\"");
            } else {
                sb.append(val);
            }
            if (it.hasNext()) sb.append(",");
        }
        sb.append("}");
        return sb.toString();
    }

    private static Duration parseTimeout(String timeout) {
        if (timeout.endsWith("ms")) return Duration.ofMillis(Long.parseLong(timeout.replace("ms", "")));
        if (timeout.endsWith("s")) return Duration.ofSeconds(Long.parseLong(timeout.replace("s", "")));
        if (timeout.endsWith("m")) return Duration.ofMinutes(Long.parseLong(timeout.replace("m", "")));
        return Duration.ofMillis(Long.parseLong(timeout));
    }
}
