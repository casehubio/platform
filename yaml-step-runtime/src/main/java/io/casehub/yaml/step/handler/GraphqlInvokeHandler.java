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

public class GraphqlInvokeHandler implements InvokeHandler {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String graphqlEndpoint;

    public GraphqlInvokeHandler(ObjectMapper objectMapper, String graphqlEndpoint) {
        this.objectMapper = objectMapper;
        this.graphqlEndpoint = graphqlEndpoint;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public boolean supports(InvokeBinding binding) {
        return binding instanceof InvokeBinding.Graphql;
    }

    @Override
    public StepAction create(StepDefinition definition, InvokeBinding binding) {
        InvokeBinding.Graphql graphql = (InvokeBinding.Graphql) binding;
        return (params, services) -> executeGraphql(graphql, params);
    }

    @SuppressWarnings("unchecked")
    private StepResult executeGraphql(InvokeBinding.Graphql graphql, Map<String, Object> params) {
        try {
            String query = interpolate(graphql.query(), params);
            Map<String, Object> requestBody = Map.of("query", query);
            String bodyJson = objectMapper.writeValueAsString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(graphqlEndpoint))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                    .build();

            long start = System.nanoTime();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long durationMs = (System.nanoTime() - start) / 1_000_000;

            Map<String, Object> metadata = Map.of("statusCode", response.statusCode(), "durationMs", durationMs);

            if (response.statusCode() >= 400) {
                return StepResult.failed("GraphQL HTTP " + response.statusCode() + ": " + response.body());
            }

            Map<String, Object> responseBody = objectMapper.readValue(response.body(), LinkedHashMap.class);
            Object data = responseBody.get("data");
            if (data instanceof Map) {
                return StepResult.of((Map<String, Object>) data, metadata);
            }
            return StepResult.of(responseBody, metadata);
        } catch (Exception e) {
            return StepResult.failed("GraphQL call failed: " + e.getMessage());
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
