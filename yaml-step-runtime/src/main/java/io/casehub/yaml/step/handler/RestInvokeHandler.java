package io.casehub.yaml.step.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.yaml.core.step.InvokeBinding;
import io.casehub.yaml.core.step.StepDefinition;
import io.casehub.yaml.plugin.api.StepAction;
import io.casehub.yaml.plugin.api.StepResult;
import io.casehub.yaml.step.InvokeHandler;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RestInvokeHandler implements InvokeHandler {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public RestInvokeHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public boolean supports(InvokeBinding binding) {
        return binding instanceof InvokeBinding.Rest;
    }

    @Override
    public StepAction create(StepDefinition definition, InvokeBinding binding) {
        InvokeBinding.Rest rest = (InvokeBinding.Rest) binding;
        return (params, services) -> executeRest(rest, params);
    }

    @SuppressWarnings("unchecked")
    private StepResult executeRest(InvokeBinding.Rest rest, Map<String, Object> params) {
        try {
            String url = interpolate(rest.url(), params);
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30));

            for (Map.Entry<String, String> header : rest.headers().entrySet()) {
                requestBuilder.header(header.getKey(), interpolate(header.getValue(), params));
            }

            if ("GET".equalsIgnoreCase(rest.method())) {
                requestBuilder.GET();
            } else {
                Map<String, String> interpolatedBody = new LinkedHashMap<>();
                for (Map.Entry<String, String> entry : rest.body().entrySet()) {
                    interpolatedBody.put(entry.getKey(), interpolate(entry.getValue(), params));
                }
                String bodyJson = objectMapper.writeValueAsString(interpolatedBody);
                requestBuilder.method(rest.method(),
                        HttpRequest.BodyPublishers.ofString(bodyJson));
                if (!rest.headers().containsKey("Content-Type")) {
                    requestBuilder.header("Content-Type", "application/json");
                }
            }

            long start = System.nanoTime();
            HttpResponse<String> response = httpClient.send(
                    requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            long durationMs = (System.nanoTime() - start) / 1_000_000;

            int statusCode = response.statusCode();
            Map<String, Object> metadata = Map.of("statusCode", statusCode, "durationMs", durationMs);

            if (statusCode >= 400) {
                return StepResult.failed("HTTP " + statusCode + ": " + response.body());
            }

            String body = response.body();
            if (body == null || body.isBlank()) {
                return StepResult.of(Map.of(), metadata);
            }

            Map<String, Object> output = objectMapper.readValue(body, LinkedHashMap.class);
            return StepResult.of(output, metadata);
        } catch (Exception e) {
            return StepResult.failed("REST call failed: " + e.getMessage());
        }
    }

    private static String interpolate(String template, Map<String, Object> params) {
        Matcher matcher = VAR_PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            Object value = params.get(key);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value != null ? value.toString() : ""));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
