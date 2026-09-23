package io.casehub.platform.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-scale LLM comprehension test: scans all casehub consumer JARs
 * from the Maven local repository, builds the real catalog, and validates
 * that an LLM can navigate the 3-tier hierarchy to discover operations.
 */
@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIf("canRun")
class FullScaleComprehensionIT {

    private static final String SYSTEM_PROMPT =
            "You are a test harness validating MCP tool comprehension. "
            + "Respond with ONLY the requested JSON — no markdown fences, "
            + "no explanation, no commentary. Raw JSON only.";

    private static final Path M2_CASEHUB = Path.of(
            System.getProperty("user.home"), ".m2", "repository", "io", "casehub");

    @Inject AgentProvider agentProvider;

    private final ObjectMapper mapper = new ObjectMapper();
    private DomainModelRegistry fullRegistry;
    private String appIndexJson;

    static boolean canRun() {
        try {
            Process p = new ProcessBuilder("claude", "--version")
                    .redirectErrorStream(true).start();
            boolean finished = p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) { p.destroyForcibly(); return false; }
            return p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    @BeforeAll
    void buildCatalog() throws Exception {
        fullRegistry = FullScaleCatalogBuilder.buildFromLocalRepo(M2_CASEHUB);

        List<String> apps = fullRegistry.getApps();
        List<DomainModel> domains = fullRegistry.getDomains();

        System.out.printf("Full-scale catalog: %d apps, %d domains, %d operations%n",
                apps.size(), domains.size(),
                domains.stream().mapToLong(d -> d.operations().size()).sum());

        for (String app : apps) {
            List<DomainModel> appDomains = fullRegistry.getDomainsByApp(app);
            System.out.printf("  %s: %d domains, %d ops%n", app, appDomains.size(),
                    appDomains.stream().mapToLong(d -> d.operations().size()).sum());
        }

        assertThat(apps.stream().filter(a -> !a.isEmpty()).toList())
                        .as("Expected at least 5 apps from CaseHub repos")
                        .hasSizeGreaterThanOrEqualTo(5);
        assertThat(domains).as("Expected at least 20 domains")
                           .hasSizeGreaterThanOrEqualTo(20);

        appIndexJson = mapper.writeValueAsString(
                DomainContentFormatter.formatAppIndex(apps, domains));
    }

    @Test
    void selectsCorrectAppForAmlIntent() throws Exception {
        String response = askClaude(
                "Here is the CaseHub application index:\n" + appIndexJson
                + "\n\nI need to investigate a suspicious financial transaction for "
                + "anti-money laundering compliance. Which app should I use? "
                + "Reply with JSON: {\"app\": \"<name>\"}");

        Map<String, Object> result = parseJson(response);
        assertThat(result.get("app")).isEqualTo("aml");
    }

    @Test
    void selectsCorrectAppForIoTIntent() throws Exception {
        String response = askClaude(
                "Here is the CaseHub application index:\n" + appIndexJson
                + "\n\nI need to check the status of a temperature sensor device. "
                + "Which app should I use? "
                + "Reply with JSON: {\"app\": \"<name>\"}");

        Map<String, Object> result = parseJson(response);
        assertThat(result.get("app")).isEqualTo("iot");
    }

    @Test
    void navigatesFromAppToDomainForClinicalTrial() throws Exception {
        List<DomainModel> clinicalDomains = fullRegistry.getDomainsByApp("clinical");
        if (clinicalDomains.isEmpty()) return;

        String appDetail = mapper.writeValueAsString(
                DomainContentFormatter.formatAppDomains("clinical", clinicalDomains));

        String response = askClaude(
                "Here are the domains in the 'clinical' app:\n" + appDetail
                + "\n\nI need to record an adverse event for a patient in a trial. "
                + "Which domain should I use? "
                + "Reply with JSON: {\"domain\": \"<name>\"}");

        Map<String, Object> result = parseJson(response);
        assertThat((String) result.get("domain"))
                .as("Should select the adverse events domain")
                .containsIgnoringCase("adverse");
    }

    @Test
    void navigatesFullHierarchyForWorkItem() throws Exception {
        // Tier 0: select app
        String t0response = askClaude(
                "Here is the CaseHub application index:\n" + appIndexJson
                + "\n\nI need to create a new work item / task. Which app? "
                + "Reply with JSON: {\"app\": \"<name>\"}");

        Map<String, Object> t0 = parseJson(t0response);
        String selectedApp = (String) t0.get("app");
        assertThat(selectedApp).isIn("work", "engine");

        // Tier 1: select domain within app
        List<DomainModel> appDomains = fullRegistry.getDomainsByApp(selectedApp);
        if (appDomains.isEmpty()) return;

        String appDetail = mapper.writeValueAsString(
                DomainContentFormatter.formatAppDomains(selectedApp, appDomains));

        String t1response = askClaude(
                "Here are the domains in the '" + selectedApp + "' app:\n" + appDetail
                + "\n\nI need to create a new work item / task. Which domain? "
                + "Reply with JSON: {\"domain\": \"<name>\"}");

        Map<String, Object> t1 = parseJson(t1response);
        String selectedDomain = (String) t1.get("domain");
        assertThat(selectedDomain).isNotBlank();

        // Tier 2: select operation
        DomainModel domain = fullRegistry.getDomain(selectedDomain).orElse(null);
        if (domain == null) return;

        String domainDetail = mapper.writeValueAsString(
                DomainContentFormatter.formatDomain(domain));

        String t2response = askClaude(
                "Here are the operations in the '" + selectedDomain + "' domain:\n"
                + domainDetail
                + "\n\nI want to create a new work item. Which operation? "
                + "Reply with JSON: {\"operation\": \"<name>\"}");

        Map<String, Object> t2 = parseJson(t2response);
        assertThat((String) t2.get("operation")).isNotBlank();
    }

    @Test
    void searchFindsRelevantOperations() throws Exception {
        List<SearchResult> results = fullRegistry.search("compliance");

        assertThat(results).as("'compliance' should match across multiple domains")
                           .hasSizeGreaterThanOrEqualTo(3);

        String searchJson = mapper.writeValueAsString(
                DomainContentFormatter.formatSearchResults("compliance", results));

        String response = askClaude(
                "Here are search results for 'compliance' across all CaseHub domains:\n"
                + searchJson
                + "\n\nI need GDPR compliance reporting. Which result is most relevant? "
                + "Reply with JSON: {\"domain\": \"<name>\", \"operation\": \"<name>\"}");

        Map<String, Object> result = parseJson(response);
        assertThat(result.get("domain")).isNotNull();
        assertThat(result.get("operation")).isNotNull();
    }

    @Test
    void catalogSizeReport() throws Exception {
        List<String> apps = fullRegistry.getApps();
        List<DomainModel> domains = fullRegistry.getDomains();
        long totalOps = domains.stream().mapToLong(d -> d.operations().size()).sum();

        System.out.println("\n=== Full-Scale Catalog Statistics ===");
        System.out.printf("Apps:       %d%n", apps.size());
        System.out.printf("Domains:    %d%n", domains.size());
        System.out.printf("Operations: %d%n", totalOps);
        System.out.printf("Avg ops/domain: %.1f%n", (double) totalOps / domains.size());
        System.out.println("\nPer-app breakdown:");
        for (String app : apps) {
            List<DomainModel> ad = fullRegistry.getDomainsByApp(app);
            long ops = ad.stream().mapToLong(d -> d.operations().size()).sum();
            System.out.printf("  %-20s %3d domains, %4d ops%n", app, ad.size(), ops);
        }
        System.out.printf("\nApp index JSON size: %d chars%n", appIndexJson.length());
        System.out.println("=== End Statistics ===\n");

        assertThat(apps.stream().filter(a -> !a.isEmpty()).count())
                .as("Expected at least 5 non-empty apps")
                .isGreaterThanOrEqualTo(5);
    }

    private String askClaude(String userPrompt) {
        var config = AgentSessionConfig.of(SYSTEM_PROMPT, userPrompt,
                Duration.ofSeconds(30));
        return agentProvider.invoke(config)
                .filter(e -> e instanceof AgentEvent.TextDelta)
                .map(e -> ((AgentEvent.TextDelta) e).text())
                .collect().with(Collectors.joining())
                .await().atMost(Duration.ofSeconds(60));
    }

    private Map<String, Object> parseJson(String response) throws Exception {
        String cleaned = response.strip();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("^```[a-z]*\\n?", "").replaceAll("\\n?```$", "");
        }
        return mapper.readValue(cleaned.strip(), new TypeReference<>() {});
    }
}
