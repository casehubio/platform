package io.casehub.platform.agent.config;

import io.casehub.platform.api.model.CostTier;
import io.casehub.platform.api.model.ModelDescriptor;
import io.casehub.platform.api.model.ModelLocality;
import io.casehub.platform.api.model.ModelTier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ManifestLoaderTest {

    private final ManifestLoader loader = new ManifestLoader();

    @Test
    void loadsSingleManifestFromClasspath() throws URISyntaxException {
        var uri = getClass().getClassLoader().getResource("manifests/base.yaml").toURI();
        var manifest = loader.loadResource(uri);
        assertThat(manifest.providers()).hasSize(1);
        assertThat(manifest.providers().get(0).vendor()).isEqualTo("anthropic");
        assertThat(manifest.defaults().backend()).isEqualTo("claude");
        assertThat(manifest.aliases()).containsKey("reasoning-heavy");
    }

    @Test
    void loadsOverrideManifest() throws URISyntaxException {
        var uri = getClass().getClassLoader().getResource("manifests/override.yaml").toURI();
        var manifest = loader.loadResource(uri);
        assertThat(manifest.providers()).hasSize(1);
        assertThat(manifest.providers().get(0).vendor()).isEqualTo("ollama");
        assertThat(manifest.defaults().backend()).isEqualTo("ollama");
    }

    @Test
    void mergeHigherPriorityWinsForModels() {
        var seedModel = testModel("claude-opus-5", ModelTier.FLAGSHIP);
        var overrideModel = testModel("claude-opus-5", ModelTier.STANDARD);

        var entries = new ArrayList<ManifestLoader.PrioritizedManifest>();
        entries.add(new ManifestLoader.PrioritizedManifest(
                manifestWith(List.of(seedModel), List.of(), Map.of()), 0));
        entries.add(new ManifestLoader.PrioritizedManifest(
                manifestWith(List.of(overrideModel), List.of(), Map.of()), 30));

        var merged = loader.merge(entries);
        assertThat(merged.models()).hasSize(1);
        assertThat(merged.models().get(0).tier()).isEqualTo(ModelTier.STANDARD);
    }

    @Test
    void mergeHigherPriorityWinsForProviders() {
        var p1 = new ProviderDeclaration("anthropic", "env:KEY_A", null);
        var p2 = new ProviderDeclaration("anthropic", "env:KEY_B", null);

        var entries = new ArrayList<ManifestLoader.PrioritizedManifest>();
        entries.add(new ManifestLoader.PrioritizedManifest(
                manifestWith(List.of(), List.of(p1), Map.of()), 10));
        entries.add(new ManifestLoader.PrioritizedManifest(
                manifestWith(List.of(), List.of(p2), Map.of()), 30));

        var merged = loader.merge(entries);
        assertThat(merged.providers()).hasSize(1);
        assertThat(merged.providers().get(0).credential()).isEqualTo("env:KEY_B");
    }

    @Test
    void mergeHigherPriorityWinsForAliases() {
        var a1 = new AliasDeclaration("FLAGSHIP", List.of("reasoning"), null, null, null, null, null);
        var a2 = new AliasDeclaration("FAST", List.of("text"), null, null, null, null, null);

        var entries = new ArrayList<ManifestLoader.PrioritizedManifest>();
        entries.add(new ManifestLoader.PrioritizedManifest(
                manifestWith(List.of(), List.of(), Map.of("fast-mode", a1)), 10));
        entries.add(new ManifestLoader.PrioritizedManifest(
                manifestWith(List.of(), List.of(), Map.of("fast-mode", a2)), 30));

        var merged = loader.merge(entries);
        assertThat(merged.aliases().get("fast-mode").tier()).isEqualTo("FAST");
    }

    @Test
    void mergeUnionOfLocalModels() {
        var lm1 = new LocalModelDeclaration("llama-4-scout", "present");
        var lm2 = new LocalModelDeclaration("codestral", "present");

        var m1 = new Manifest(List.of(), List.of(), List.of(), Map.of(), List.of(lm1), null, Map.of());
        var m2 = new Manifest(List.of(), List.of(), List.of(), Map.of(), List.of(lm2), null, Map.of());

        var entries = new ArrayList<ManifestLoader.PrioritizedManifest>();
        entries.add(new ManifestLoader.PrioritizedManifest(m1, 10));
        entries.add(new ManifestLoader.PrioritizedManifest(m2, 20));

        var merged = loader.merge(entries);
        assertThat(merged.localModels()).hasSize(2);
    }

    @Test
    void mergeDefaultsLastWriterWins() {
        var entries = new ArrayList<ManifestLoader.PrioritizedManifest>();
        entries.add(new ManifestLoader.PrioritizedManifest(
                new Manifest(List.of(), List.of(), List.of(), Map.of(), List.of(), new ManifestDefaults("claude"), Map.of()), 10));
        entries.add(new ManifestLoader.PrioritizedManifest(
                new Manifest(List.of(), List.of(), List.of(), Map.of(), List.of(), new ManifestDefaults("ollama"), Map.of()), 30));

        var merged = loader.merge(entries);
        assertThat(merged.defaults().backend()).isEqualTo("ollama");
    }

    @Test
    void loadFileReturnsNullForMissing(@TempDir Path tempDir) {
        assertThat(loader.loadFile(tempDir.resolve("nonexistent.yaml"))).isNull();
    }

    @Test
    void loadFileParsesYaml(@TempDir Path tempDir) throws IOException {
        var file = tempDir.resolve("agent-config.yaml");
        Files.writeString(file, """
                providers:
                  - vendor: openai
                    credential: env:OPENAI_KEY
                defaults:
                  backend: openai
                """);
        var manifest = loader.loadFile(file);
        assertThat(manifest).isNotNull();
        assertThat(manifest.providers()).hasSize(1);
        assertThat(manifest.providers().get(0).vendor()).isEqualTo("openai");
    }

    @Test
    void loadDiscoverProjectConfig(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("agent-config.yaml"), """
                providers:
                  - vendor: ollama
                defaults:
                  backend: ollama
                """);
        var manifest = loader.load(tempDir, null);
        assertThat(manifest.providers().stream()
                .anyMatch(p -> "ollama".equals(p.vendor()))).isTrue();
    }

    @Test
    void loadProfileSpecificFile(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("agent-config.yaml"), """
                defaults:
                  backend: claude
                """);
        Files.writeString(tempDir.resolve("agent-config-ci.yaml"), """
                defaults:
                  backend: ollama
                """);
        var manifest = loader.load(tempDir, "ci");
        assertThat(manifest.defaults().backend()).isEqualTo("ollama");
    }

    @Test
    void loadWithoutProfileIgnoresProfileFile(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("agent-config.yaml"), """
                defaults:
                  backend: claude
                """);
        Files.writeString(tempDir.resolve("agent-config-ci.yaml"), """
                defaults:
                  backend: ollama
                """);
        var manifest = loader.load(tempDir, null);
        assertThat(manifest.defaults().backend()).isEqualTo("claude");
    }


    @Test
    void loadFileParsesPools(@TempDir Path tempDir) throws IOException {
        var file = tempDir.resolve("agent-config.yaml");
        Files.writeString(file, """
                                pools:
                                  review-pool:
                                    agent-id: code-reviewer
                                    backend: claudony
                                    min-active: 2
                                    max-active: 8
                                    working-dir: ~/workspace/reviews
                                    scaling:
                                      type: target-tracking
                                      target: 0.7
                                """);
        var manifest = loader.loadFile(file);
        assertThat(manifest).isNotNull();
        assertThat(manifest.pools()).hasSize(1);
        assertThat(manifest.pools()).containsKey("review-pool");
        var pool = manifest.pools().get("review-pool");
        assertThat(pool.name()).isEqualTo("review-pool");
        assertThat(pool.agentId()).isEqualTo("code-reviewer");
        assertThat(pool.backend()).isEqualTo("claudony");
        assertThat(pool.minActive()).isEqualTo(2);
        assertThat(pool.maxActive()).isEqualTo(8);
        assertThat(pool.workingDir()).isEqualTo("~/workspace/reviews");
        assertThat(pool.scaling()).containsEntry("type", "target-tracking");
    }

    @Test
    void loadFileNoPoolsSectionReturnsEmptyMap(@TempDir Path tempDir) throws IOException {
        var file = tempDir.resolve("agent-config.yaml");
        Files.writeString(file, """
                                providers:
                                  - vendor: openai
                                    credential: env:OPENAI_KEY
                                """);
        var manifest = loader.loadFile(file);
        assertThat(manifest).isNotNull();
        assertThat(manifest.pools()).isEmpty();
    }

    @Test
    void mergeHigherPriorityWinsForPools() {
        var p1 = new PoolDeclaration("review-pool", "agent-a", "claudony", 1, 4, null, null, null);
        var p2 = new PoolDeclaration("review-pool", "agent-b", "claudony", 2, 8, null, null, null);
        var p3 = new PoolDeclaration("other-pool", "agent-c", "claudony", 0, 2, null, null, null);

        var entries = new ArrayList<ManifestLoader.PrioritizedManifest>();
        entries.add(new ManifestLoader.PrioritizedManifest(
                new Manifest(List.of(), List.of(), List.of(), Map.of(), List.of(), null,
                             Map.of("review-pool", p1, "other-pool", p3)), 10));
        entries.add(new ManifestLoader.PrioritizedManifest(
                new Manifest(List.of(), List.of(), List.of(), Map.of(), List.of(), null,
                             Map.of("review-pool", p2)), 30));

        var merged = loader.merge(entries);
        assertThat(merged.pools()).hasSize(2);
        assertThat(merged.pools().get("review-pool").agentId()).isEqualTo("agent-b");
        assertThat(merged.pools().get("other-pool").agentId()).isEqualTo("agent-c");
    }

    private ModelDescriptor testModel(String id, ModelTier tier) {
        return new ModelDescriptor(id, id, "backend", null, "vendor", "fam", id, tier,
                Set.of("text"), 200000, 16384, ModelLocality.CLOUD, CostTier.MEDIUM, "api-key", Map.of());
    }

    private Manifest manifestWith(List<ModelDescriptor> models, List<ProviderDeclaration> providers,
                                  Map<String, AliasDeclaration> aliases) {
        return new Manifest(models, providers, List.of(), aliases, List.of(), null, Map.of());
    }
}
