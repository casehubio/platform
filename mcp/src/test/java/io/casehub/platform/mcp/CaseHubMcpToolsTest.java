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
    void tier0p5AppReturnsDomainListWithEnricherState() throws Exception {
        String              json   = tools.casehub_model(null, "test-app");
        Map<String, Object> result = mapper.readValue(json, new TypeReference<>() {});

        assertThat(result).containsKey("domains");
        List<Map<String, Object>> domains =
                (List<Map<String, Object>>) result.get("domains");
        Map<String, Object> testDomain = domains.stream()
                                                .filter(d -> "test".equals(d.get("name"))).findFirst().orElseThrow();
        assertThat(testDomain).containsEntry("summary", "Test domain — echo messages, store values, create items");
        assertThat(testDomain).containsKey("state");
    }

    @Test
    void tier0WithBlankDomainReturnsApps() throws Exception {
        String              json   = tools.casehub_model("", null);
        Map<String, Object> result = mapper.readValue(json, new TypeReference<>() {});
        assertThat(result).containsKey("apps");
    }

    @Test
    void tier1ReturnsOperationDetail() throws Exception {
        String              json   = tools.casehub_model("test", null);
        Map<String, Object> result = mapper.readValue(json, new TypeReference<>() {});

        assertThat(result).containsEntry("domain", "test");
        assertThat(result).containsKey("queries");
        assertThat(result).containsKey("mutations");
    }

    @Test
    @SuppressWarnings("unchecked")
    void tier1QueriesHaveDescriptions() throws Exception {
        String              json   = tools.casehub_model("test", null);
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
        assertThatThrownBy(() -> tools.casehub_model("nonexistent", null))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchByOperationNameReturnsMatches() throws Exception {
        String              json   = tools.casehub_search("echo");
        Map<String, Object> result = mapper.readValue(json, new TypeReference<>() {});

        assertThat(result).containsEntry("query", "echo");
        assertThat((int) result.get("resultCount")).isGreaterThan(0);

        List<Map<String, Object>> results =
                (List<Map<String, Object>>) result.get("results");
        assertThat(results).anyMatch(r ->
                                             "echo".equals(r.get("operation")) && "test".equals(r.get("domain")));
    }

    @Test
    void searchIsCaseInsensitive() throws Exception {
        String              json   = tools.casehub_search("ECHO");
        Map<String, Object> result = mapper.readValue(json, new TypeReference<>() {});
        assertThat((int) result.get("resultCount")).isGreaterThan(0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchBySummaryReturnsMatches() throws Exception {
        String              json   = tools.casehub_search("greeting");
        Map<String, Object> result = mapper.readValue(json, new TypeReference<>() {});

        List<Map<String, Object>> results =
                (List<Map<String, Object>>) result.get("results");
        assertThat(results).anyMatch(r -> "hello".equals(r.get("operation")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchByParamNameReturnsMatches() throws Exception {
        String              json   = tools.casehub_search("message");
        Map<String, Object> result = mapper.readValue(json, new TypeReference<>() {});

        List<Map<String, Object>> results =
                (List<Map<String, Object>>) result.get("results");
        assertThat(results).anyMatch(r -> "echo".equals(r.get("operation")));
    }

    @Test
    void searchWithNoMatchesReturnsEmpty() throws Exception {
        String              json   = tools.casehub_search("zzz_nonexistent_zzz");
        Map<String, Object> result = mapper.readValue(json, new TypeReference<>() {});

        assertThat(result).containsEntry("query", "zzz_nonexistent_zzz");
        assertThat(result).containsEntry("resultCount", 0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchByReturnTypeReturnsMatches() throws Exception {
        String              json   = tools.casehub_search("String");
        Map<String, Object> result = mapper.readValue(json, new TypeReference<>() {});

        List<Map<String, Object>> results =
                (List<Map<String, Object>>) result.get("results");
        assertThat(results).isNotEmpty();
        assertThat(results).anyMatch(r -> "echo".equals(r.get("operation")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchResultIncludesOperationType() throws Exception {
        String              json   = tools.casehub_search("store");
        Map<String, Object> result = mapper.readValue(json, new TypeReference<>() {});

        List<Map<String, Object>> results =
                (List<Map<String, Object>>) result.get("results");
        Map<String, Object> storeResult = results.stream()
                                                 .filter(r -> "store".equals(r.get("operation")))
                                                 .findFirst().orElseThrow();
        assertThat(storeResult).containsEntry("type", "MUTATION");
        assertThat(storeResult).containsKey("summary");
        assertThat(storeResult).containsKey("params");
        assertThat(storeResult).containsKey("returns");
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchAcrossDomains() throws Exception {
        String              json   = tools.casehub_search("status");
        Map<String, Object> result = mapper.readValue(json, new TypeReference<>() {});

        List<Map<String, Object>> results =
                (List<Map<String, Object>>) result.get("results");
        assertThat(results).anyMatch(r -> "class-based".equals(r.get("domain")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void tier0ReturnsAppList() throws Exception {
        String              json   = tools.casehub_model(null, null);
        Map<String, Object> result = mapper.readValue(json, new TypeReference<>() {});

        assertThat(result).containsKey("apps");
        List<Map<String, Object>> apps =
                (List<Map<String, Object>>) result.get("apps");
        assertThat(apps).anyMatch(a -> "test-app".equals(a.get("name")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void tier0AppSummaryIncludesDomainCount() throws Exception {
        String              json   = tools.casehub_model(null, null);
        Map<String, Object> result = mapper.readValue(json, new TypeReference<>() {});
        List<Map<String, Object>> apps =
                (List<Map<String, Object>>) result.get("apps");
        Map<String, Object> testApp = apps.stream()
                                          .filter(a -> "test-app".equals(a.get("name"))).findFirst().orElseThrow();
        assertThat(testApp).containsKey("domainCount");
        assertThat((int) testApp.get("domainCount")).isEqualTo(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void tier0p5AppReturnsDomainList() throws Exception {
        String              json   = tools.casehub_model(null, "test-app");
        Map<String, Object> result = mapper.readValue(json, new TypeReference<>() {});

        assertThat(result).containsEntry("app", "test-app");
        assertThat(result).containsKey("domains");
        List<Map<String, Object>> domains =
                (List<Map<String, Object>>) result.get("domains");
        assertThat(domains).anyMatch(d -> "test".equals(d.get("name")));
    }

    @Test
    void tier0p5UnknownAppThrows() {
        assertThatThrownBy(() -> tools.casehub_model(null, "nonexistent"))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void domainsWithoutAppGroupedByDomainName() throws Exception {
        String              json   = tools.casehub_model(null, null);
        Map<String, Object> result = mapper.readValue(json, new TypeReference<>() {});
        List<Map<String, Object>> apps =
                (List<Map<String, Object>>) result.get("apps");
        assertThat(apps).anyMatch(a -> "class-based".equals(a.get("name")));
    }


    @Test
    @SuppressWarnings("unchecked")
    void tier1IncludesEvents() throws Exception {
        String              json   = tools.casehub_model("test", null);
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
        String              json   = tools.casehub_model("test", null);
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
