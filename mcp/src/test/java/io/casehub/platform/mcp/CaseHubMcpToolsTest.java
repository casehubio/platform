package io.casehub.platform.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@QuarkusTest
class CaseHubMcpToolsTest {

    @Inject
    CaseHubMcpTools tools;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @SuppressWarnings("unchecked")
    void tier0ReturnsDomainList() throws Exception {
        String json = tools.casehub_model(null);
        Map<String, Object> result = mapper.readValue(json, new TypeReference<>() {});

        assertThat(result).containsKey("domains");
        List<Map<String, Object>> domains =
                (List<Map<String, Object>>) result.get("domains");
        assertThat(domains).anyMatch(d -> "test".equals(d.get("name")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void tier0IncludesEnricherState() throws Exception {
        String json = tools.casehub_model(null);
        Map<String, Object> result = mapper.readValue(json, new TypeReference<>() {});
        List<Map<String, Object>> domains =
                (List<Map<String, Object>>) result.get("domains");
        Map<String, Object> testDomain = domains.stream()
                .filter(d -> "test".equals(d.get("name"))).findFirst().orElseThrow();

        assertThat(testDomain).containsEntry("summary", "Test domain for verification");
        assertThat(testDomain).containsKey("state");
    }

    @Test
    void tier0WithBlankDomain() throws Exception {
        String json = tools.casehub_model("");
        Map<String, Object> result = mapper.readValue(json, new TypeReference<>() {});
        assertThat(result).containsKey("domains");
    }

    @Test
    void tier1ReturnsOperationDetail() throws Exception {
        String json = tools.casehub_model("test");
        Map<String, Object> result = mapper.readValue(json, new TypeReference<>() {});

        assertThat(result).containsEntry("domain", "test");
        assertThat(result).containsKey("queries");
        assertThat(result).containsKey("mutations");
    }

    @Test
    @SuppressWarnings("unchecked")
    void tier1QueriesHaveDescriptions() throws Exception {
        String json = tools.casehub_model("test");
        Map<String, Object> result = mapper.readValue(json, new TypeReference<>() {});
        List<Map<String, Object>> queries =
                (List<Map<String, Object>>) result.get("queries");
        Map<String, Object> echo = queries.stream()
                .filter(q -> "echo".equals(q.get("name"))).findFirst().orElseThrow();

        assertThat(echo).containsEntry("summary", "Echo the input back");
        assertThat(echo).containsKey("params");
    }

    @Test
    void tier1UnknownDomainThrows() {
        assertThatThrownBy(() -> tools.casehub_model("nonexistent"))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void tier1IncludesEvents() throws Exception {
        String              json   = tools.casehub_model("test");
        Map<String, Object> result = mapper.readValue(json, new TypeReference<>() {});
        List<Map<String, Object>> events =
                (List<Map<String, Object>>) result.get("events");

        assertThat(events).isNotNull().hasSize(1);
        assertThat(events.get(0)).containsEntry("name", "caseLifecycle");
        assertThat(events.get(0)).containsEntry("delivery", "qhorus");
        assertThat(events.get(0)).containsEntry("channel", "test-case-lifecycle");
    }

    @Test
    @SuppressWarnings("unchecked")
    void tier1MutationParamsIncludeFieldExpansion() throws Exception {
        String              json   = tools.casehub_model("test");
        Map<String, Object> result = mapper.readValue(json, new TypeReference<>() {});
        List<Map<String, Object>> mutations =
                (List<Map<String, Object>>) result.get("mutations");
        Map<String, Object> create = mutations.stream()
                                              .filter(m -> "create".equals(m.get("name"))).findFirst().orElseThrow();
        List<Map<String, Object>> params =
                (List<Map<String, Object>>) create.get("params");
        Map<String, Object> inputParam = params.get(0);

        assertThat(inputParam).containsEntry("name", "input");
        assertThat(inputParam).containsEntry("type", "TestInput");
        assertThat(inputParam).containsKey("fields");
        Map<String, String> fields = (Map<String, String>) inputParam.get("fields");
        assertThat(fields).containsEntry("name", "String");
        assertThat(fields).containsEntry("count", "Integer");
    }

}
