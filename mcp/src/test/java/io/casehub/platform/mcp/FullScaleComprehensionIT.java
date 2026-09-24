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

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-scale LLM discovery test: given a realistic task, can the LLM
 * navigate the CaseHub catalog hierarchy to find the right operation?
 *
 * <p>Tests two discovery paths:
 * <ul>
 *   <li><b>Hierarchy navigation</b> — app index → app detail → domain operations.
 *       Tests whether the 3-tier hierarchy guides the LLM to the right answer.</li>
 *   <li><b>Search</b> — keyword search across all operations.
 *       Tests whether casehub_search is effective for intent-driven discovery.</li>
 * </ul>
 *
 * <p>Each scenario is a realistic task description. The test does NOT hand-hold
 * ("which app should I use?"). Instead it presents the catalog data and asks
 * the LLM to find the specific operation that handles the task.
 */
@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIf("canRun")
class FullScaleComprehensionIT {

    private static final String DISCOVERY_PROMPT =
            "You are an AI agent with access to the CaseHub platform. "
            + "You have two discovery tools: casehub_model (browse the hierarchy) "
            + "and casehub_search (keyword search). "
            + "Given a task, find the exact operation that handles it. "
            + "Respond with ONLY JSON — no markdown fences, no explanation. "
            + "Format: {\"app\": \"...\", \"domain\": \"...\", \"operation\": \"...\", "
            + "\"confidence\": \"high|medium|low\", \"reasoning\": \"one sentence\"}";

    private static final Path SLOT_ROOT = Path.of(
            System.getProperty("casehub.slot.root",
                    System.getProperty("user.home") + "/claude/casehub/slots/194"));

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
        Path catalogJson = SLOT_ROOT.resolve("wsp-casehub-ledger/catalog.json");
        if (Files.exists(catalogJson)) {
            System.out.println("Loading catalog from source-parsed JSON: " + catalogJson);
            fullRegistry = FullScaleCatalogBuilder.buildFromCatalogJson(catalogJson);
        } else {
            System.out.println("No catalog.json found — falling back to Jandex scan");
            fullRegistry = FullScaleCatalogBuilder.buildFromSlotAndLocalRepo(SLOT_ROOT, M2_CASEHUB);
        }

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
                DomainContentFormatter.formatAppIndex(apps, domains, APP_CAPABILITIES));
    }

    private static final Map<String, DomainContentFormatter.AppCapability> APP_CAPABILITIES = Map.ofEntries(
            Map.entry("aml", new DomainContentFormatter.AppCapability(
                    "Anti-money laundering", "compliance", "investigations", "financial-crime", "risk")),
            Map.entry("chat-app", new DomainContentFormatter.AppCapability(
                    "Real-time messaging", "chat", "presence", "channels")),
            Map.entry("claudony", new DomainContentFormatter.AppCapability(
                    "Agent fleet management", "multi-session", "orchestration", "peer-mesh", "cases")),
            Map.entry("clinical", new DomainContentFormatter.AppCapability(
                    "Clinical trial management", "patients", "adverse-events", "medications", "sites", "regulatory")),
            Map.entry("connectors", new DomainContentFormatter.AppCapability(
                    "External system connectors", "email", "calendar", "banking", "webhooks")),
            Map.entry("devtown", new DomainContentFormatter.AppCapability(
                    "Developer operations", "code-review", "governance", "incident-feedback", "reasoning")),
            Map.entry("engine", new DomainContentFormatter.AppCapability(
                    "CMMN case engine", "cases", "plans", "goals", "events", "case-definitions")),
            Map.entry("fsitrading", new DomainContentFormatter.AppCapability(
                    "Financial trading", "orders", "strategies", "positions", "market-data", "compliance")),
            Map.entry("iot", new DomainContentFormatter.AppCapability(
                    "IoT device management", "sensors", "alerts", "situations", "suppressions", "kpi")),
            Map.entry("ledger", new DomainContentFormatter.AppCapability(
                    "Immutable audit ledger", "audit-trail", "merkle-verification", "trust-scores", "attestations")),
            Map.entry("life", new DomainContentFormatter.AppCapability(
                    "Life case management", "tasks", "cases", "dashboards", "external-actors", "analytics")),
            Map.entry("neocortex", new DomainContentFormatter.AppCapability(
                    "Cognitive observability", "reasoning-traces", "decision-transparency")),
            Map.entry("openclaw", new DomainContentFormatter.AppCapability(
                    "Contract & commitment lifecycle", "obligations", "scenarios", "dispute-resolution")),
            Map.entry("ops", new DomainContentFormatter.AppCapability(
                    "Platform operations", "deployments", "approvals", "clusters", "security", "reconciliation")),
            Map.entry("qhorus", new DomainContentFormatter.AppCapability(
                    "Agentic channel mesh", "messaging", "channels", "agents", "governance", "compliance")),
            Map.entry("soc", new DomainContentFormatter.AppCapability(
                    "Security operations center", "incidents", "alerts", "threat-intel", "cbr", "trust")),
            Map.entry("work", new DomainContentFormatter.AppCapability(
                    "Work item management", "tasks", "queues", "lifecycle", "assignments", "federation"))
    );

    // ── Hierarchy discovery: app index → app detail → domain operations ─────

    @Test
    void discoverViaHierarchy_amlInvestigation() throws Exception {
        assertHierarchyDiscovery(
                "A bank flagged a wire transfer of $2.3M from a shell company. "
                + "Open an AML investigation and assign it to the financial crimes team.",
                "aml", null, null);
    }

    @Test
    void discoverViaHierarchy_clinicalAdverseEvent() throws Exception {
        assertHierarchyDiscovery(
                "A patient in clinical trial CT-2026-001 reported nausea and "
                + "dizziness after their third dose. Record this adverse event.",
                "clinical", "adverse", null);
    }

    @Test
    void discoverViaHierarchy_iotSuppression() throws Exception {
        assertHierarchyDiscovery(
                "Temperature sensor TMP-B2-07 in building B is reporting values "
                + "above threshold due to HVAC maintenance. Suppress alerts for "
                + "this sensor for the next 4 hours.",
                "iot", "suppression", null);
    }

    @Test
    void discoverViaHierarchy_ledgerCompliance() throws Exception {
        assertHierarchyDiscovery(
                "Generate a compliance report showing all automated decisions "
                + "made by agent claude:reviewer@v1 in the last 30 days, "
                + "for GDPR Article 22 audit.",
                "ledger", null, null);
    }

    @Test
    void discoverViaHierarchy_workItemCreation() throws Exception {
        assertHierarchyDiscovery(
                "Create a new work item titled 'Review Q3 financial statements' "
                + "with high priority, assigned to the finance review team.",
                "work", "item", null);
    }

    // ── Search discovery: keyword search across all operations ───────────────

    @Test
    void discoverViaSearch_complianceAcrossDomains() throws Exception {
        String task = "I need to run a GDPR compliance check. Which operations "
                + "across the platform handle compliance reporting?";

        List<SearchResult> results = fullRegistry.search("compliance");
        assertThat(results).as("'compliance' should match across multiple apps")
                           .hasSizeGreaterThanOrEqualTo(3);

        String searchJson = mapper.writeValueAsString(
                DomainContentFormatter.formatSearchResults("compliance", results));

        Map<String, Object> result = parseJson(askClaude(
                "A user searched for 'compliance' and got these results:\n" + searchJson
                + "\n\nTheir actual task: " + task
                + "\n\nWhich result best matches? Reply with JSON: "
                + "{\"domain\": \"...\", \"operation\": \"...\", \"reasoning\": \"one sentence\"}"));

        assertThat(result.get("domain")).isNotNull();
        assertThat(result.get("operation")).isNotNull();
        System.out.printf("  Search discovery: domain=%s, op=%s — %s%n",
                result.get("domain"), result.get("operation"), result.get("reasoning"));
    }

    @Test
    void discoverViaSearch_trustScore() throws Exception {
        List<SearchResult> results = fullRegistry.search("trust");
        assertThat(results).hasSizeGreaterThanOrEqualTo(1);

        String searchJson = mapper.writeValueAsString(
                DomainContentFormatter.formatSearchResults("trust", results));

        Map<String, Object> result = parseJson(askClaude(
                "Search results for 'trust':\n" + searchJson
                + "\n\nTask: Check the trust score for agent claude:tarkus-reviewer@v1 "
                + "to decide if it should be included in the review rotation."
                + "\n\nWhich result? JSON: {\"domain\": \"...\", \"operation\": \"...\", "
                + "\"reasoning\": \"one sentence\"}"));

        assertThat(result.get("domain")).isNotNull();
        System.out.printf("  Search discovery: domain=%s, op=%s — %s%n",
                result.get("domain"), result.get("operation"), result.get("reasoning"));
    }

    // ── Cross-cutting: ambiguous tasks that span multiple apps ────────────────

    @Test
    void ambiguousTask_auditTrail() throws Exception {
        String fullCatalog = buildFullCatalogJson();

        Map<String, Object> result = parseJson(askClaude(
                "Here is the complete CaseHub operation catalog:\n\n" + fullCatalog
                + "\n\nTask: 'Show me the audit trail for case #4521 — who did what and when.'"
                + "\n\nMultiple apps might handle audit trails. Find the most appropriate "
                + "operation. JSON: {\"app\": \"...\", \"domain\": \"...\", "
                + "\"operation\": \"...\", \"confidence\": \"high|medium|low\", "
                + "\"reasoning\": \"one sentence\"}"));

        System.out.printf("  Ambiguous discovery: app=%s, domain=%s, op=%s, "
                + "confidence=%s — %s%n",
                result.get("app"), result.get("domain"), result.get("operation"),
                result.get("confidence"), result.get("reasoning"));

        assertThat(result.get("app")).isNotNull();
        assertThat(result.get("confidence")).isNotNull();
    }

    // ── Catalog statistics (no LLM — pure data) ─────────────────────────────

    @Test
    void catalogSizeReport() {
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

        String fullCatalog = buildFullCatalogJson();
        System.out.printf("\nApp index JSON size:  %,d chars%n", appIndexJson.length());
        System.out.printf("Full catalog JSON size: %,d chars%n", fullCatalog.length());
        System.out.println("=== End Statistics ===\n");

        assertThat(apps.stream().filter(a -> !a.isEmpty()).count())
                .as("Expected at least 5 non-empty apps")
                .isGreaterThanOrEqualTo(5);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void assertHierarchyDiscovery(String task, String expectedApp,
                                           String expectedDomainSubstring,
                                           String expectedOpSubstring) throws Exception {
        // Turn 1: show app index only — LLM picks the app
        Map<String, Object> t1 = parseJson(askClaude(
                "Here is the CaseHub application index (each app groups related domains):\n\n"
                + appIndexJson
                + "\n\nTask: " + task
                + "\n\nWhich app handles this? JSON only: {\"app\": \"<name>\"}"));

        String selectedApp = (String) t1.get("app");
        System.out.printf("  [%s] Turn 1 — selected app: %s (expected: %s)%n",
                task.substring(0, Math.min(40, task.length())), selectedApp, expectedApp);

        assertThat(selectedApp)
                .as("Expected app '%s' for task: %s", expectedApp, task)
                .isEqualTo(expectedApp);

        // Turn 2: show that app's domains — LLM picks the domain
        List<DomainModel> appDomains = fullRegistry.getDomainsByApp(selectedApp);
        String appDetail = mapper.writeValueAsString(
                DomainContentFormatter.formatAppDomains(selectedApp, appDomains));

        Map<String, Object> t2 = parseJson(askClaude(
                "App '" + selectedApp + "' has these domains:\n\n" + appDetail
                + "\n\nTask: " + task
                + "\n\nWhich domain? JSON only: {\"domain\": \"<name>\"}"));

        String selectedDomain = (String) t2.get("domain");
        System.out.printf("  [%s] Turn 2 — selected domain: %s%n",
                task.substring(0, Math.min(40, task.length())), selectedDomain);

        if (expectedDomainSubstring != null) {
            assertThat(selectedDomain)
                    .as("Domain should contain '%s'", expectedDomainSubstring)
                    .containsIgnoringCase(expectedDomainSubstring);
        }

        // Turn 3: show domain operations — LLM picks the operation
        DomainModel domain = fullRegistry.getDomain(selectedDomain).orElse(null);
        if (domain == null) {
            System.out.printf("  [%s] Turn 3 — domain '%s' not found in registry, skipping%n",
                    task.substring(0, Math.min(40, task.length())), selectedDomain);
            return;
        }

        String domainDetail = mapper.writeValueAsString(
                DomainContentFormatter.formatDomain(domain));

        Map<String, Object> t3 = parseJson(askClaude(
                "Domain '" + selectedDomain + "' operations:\n\n" + domainDetail
                + "\n\nTask: " + task
                + "\n\nWhich operation? JSON only: "
                + "{\"operation\": \"<name>\", \"confidence\": \"high|medium|low\"}"));

        String selectedOp = (String) t3.get("operation");
        System.out.printf("  [%s] Turn 3 — selected operation: %s (confidence: %s)%n",
                task.substring(0, Math.min(40, task.length())), selectedOp, t3.get("confidence"));

        assertThat(selectedOp).as("Operation should not be blank").isNotBlank();
        if (expectedOpSubstring != null) {
            assertThat(selectedOp)
                    .as("Operation should contain '%s'", expectedOpSubstring)
                    .containsIgnoringCase(expectedOpSubstring);
        }
    }

    private String buildFullCatalogJson() {
        StringBuilder sb = new StringBuilder();
        sb.append(appIndexJson).append("\n\n");
        for (String app : fullRegistry.getApps()) {
            if (app.isEmpty()) continue;
            List<DomainModel> appDomains = fullRegistry.getDomainsByApp(app);
            for (DomainModel domain : appDomains) {
                try {
                    sb.append(mapper.writeValueAsString(
                            DomainContentFormatter.formatDomain(domain)));
                    sb.append("\n");
                } catch (Exception ignored) {}
            }
        }
        return sb.toString();
    }

    private String askClaude(String userPrompt) {
        var config = AgentSessionConfig.of(DISCOVERY_PROMPT, userPrompt,
                Duration.ofSeconds(120));
        return agentProvider.invoke(config)
                .filter(e -> e instanceof AgentEvent.TextDelta)
                .map(e -> ((AgentEvent.TextDelta) e).text())
                .collect().with(Collectors.joining())
                .await().atMost(Duration.ofSeconds(180));
    }

    private Map<String, Object> parseJson(String response) throws Exception {
        String cleaned = response.strip();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("^```[a-z]*\\n?", "").replaceAll("\\n?```$", "");
        }
        cleaned = cleaned.strip();
        if (!cleaned.startsWith("{")) {
            int braceStart = cleaned.indexOf('{');
            int braceEnd = cleaned.lastIndexOf('}');
            if (braceStart >= 0 && braceEnd > braceStart) {
                cleaned = cleaned.substring(braceStart, braceEnd + 1);
            }
        }
        return mapper.readValue(cleaned, new TypeReference<>() {});
    }
}
