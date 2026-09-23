package io.casehub.platform.drift;

import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SpringParityRuleTest {

    @TempDir Path tempDir;

    private MavenProject createProject(String artifactId) {
        return createProject(artifactId, artifactId);
    }

    private MavenProject createProject(String artifactId, String dirName) {
        Model model = new Model();
        model.setGroupId("io.casehub");
        model.setArtifactId("casehub-platform-" + artifactId);
        model.setVersion("0.2-SNAPSHOT");
        model.setPackaging("jar");
        Build build = new Build();
        model.setBuild(build);
        MavenProject project = new MavenProject(model);
        File baseDir = tempDir.resolve(dirName).toFile();
        baseDir.mkdirs();
        project.setFile(new File(baseDir, "pom.xml"));
        return project;
    }

    private void addGeneratorPlugin(MavenProject project, String generatorArtifact,
                                     String quarkusModulePath, boolean includeVerify) {
        Build build = project.getModel().getBuild();
        Plugin plugin = new Plugin();
        plugin.setGroupId("io.casehub");
        plugin.setArtifactId(generatorArtifact);

        PluginExecution generate = new PluginExecution();
        generate.setId("generate");
        generate.addGoal("generate");
        Xpp3Dom genConfig = new Xpp3Dom("configuration");
        Xpp3Dom quarkusModule = new Xpp3Dom("quarkusModule");
        quarkusModule.setValue(quarkusModulePath);
        genConfig.addChild(quarkusModule);
        generate.setConfiguration(genConfig);
        plugin.addExecution(generate);

        if (includeVerify) {
            PluginExecution verify = new PluginExecution();
            verify.setId("verify-drift");
            verify.addGoal("verify");
            plugin.addExecution(verify);
        }

        build.addPlugin(plugin);
    }

    @Test
    void uncoveredModule_detected() {
        MavenProject governance = createProject("governance");
        MavenProject governanceSpring = createProject("governance-spring");
        addGeneratorPlugin(governanceSpring, "casehub-platform-spring-generator",
                tempDir.resolve("governance").toAbsolutePath().toString(), true);
        MavenProject uncovered = createProject("notifications");

        List<MavenProject> reactor = List.of(governance, governanceSpring, uncovered);
        SpringParityRule rule = new SpringParityRule();

        Set<String> covered = rule.buildCoveredSet(reactor);
        Set<String> candidates = rule.buildCandidateSet(reactor);
        DriftDetectionRule.AllowList allowList = new DriftDetectionRule.AllowList(Set.of(), Set.of());

        List<String> violations = rule.checkModuleCoverage(candidates, covered, allowList);
        assertThat(violations).hasSize(1);
        assertThat(violations.get(0)).contains("notifications");
    }

    @Test
    void coveredByGenerator_passes() {
        MavenProject governance = createProject("governance");
        MavenProject governanceSpring = createProject("governance-spring");
        addGeneratorPlugin(governanceSpring, "casehub-platform-spring-generator",
                tempDir.resolve("governance").toAbsolutePath().toString(), true);

        List<MavenProject> reactor = List.of(governance, governanceSpring);
        SpringParityRule rule = new SpringParityRule();

        Set<String> covered = rule.buildCoveredSet(reactor);
        Set<String> candidates = rule.buildCandidateSet(reactor);
        DriftDetectionRule.AllowList allowList = new DriftDetectionRule.AllowList(Set.of(), Set.of());

        List<String> violations = rule.checkModuleCoverage(candidates, covered, allowList);
        assertThat(violations).isEmpty();
    }

    @Test
    void coveredByHandWrittenSpringModule_passes() {
        MavenProject callback = createProject("callback");
        MavenProject callbackSpring = createProject("callback-spring");

        List<MavenProject> reactor = List.of(callback, callbackSpring);
        SpringParityRule rule = new SpringParityRule();

        Set<String> covered = rule.buildCoveredSet(reactor);
        assertThat(covered).contains("casehub-platform-callback");
    }

    @Test
    void exceptionedModule_passes() {
        MavenProject uncovered = createProject("notifications");

        List<MavenProject> reactor = List.of(uncovered);
        SpringParityRule rule = new SpringParityRule();

        Set<String> covered = rule.buildCoveredSet(reactor);
        Set<String> candidates = rule.buildCandidateSet(reactor);
        DriftDetectionRule.AllowList allowList = new DriftDetectionRule.AllowList(
                Set.of("notifications"), Set.of());

        List<String> violations = rule.checkModuleCoverage(candidates, covered, allowList);
        assertThat(violations).isEmpty();
    }

    @Test
    void autoExcludedModules_notCandidates() {
        MavenProject core = createProject("governance-core");
        MavenProject api = createProject("platform-api");
        MavenProject spring = createProject("governance-spring");
        MavenProject jpaCommon = createProject("acl-jpa-common");
        MavenProject jpa = createProject("acl-jpa");
        MavenProject testing = createProject("spring-testing");
        MavenProject generator = createProject("spring-generator");
        MavenProject starter = createProject("spring-boot-starter");

        List<MavenProject> reactor = List.of(core, api, spring, jpaCommon, jpa,
                testing, generator, starter);
        SpringParityRule rule = new SpringParityRule();

        Set<String> candidates = rule.buildCandidateSet(reactor);
        assertThat(candidates).isEmpty();
    }

    @Test
    void generatorMissingVerify_detected() {
        MavenProject governanceSpring = createProject("governance-spring");
        addGeneratorPlugin(governanceSpring, "casehub-platform-spring-generator",
                tempDir.resolve("governance").toAbsolutePath().toString(), false);

        List<MavenProject> reactor = List.of(governanceSpring);
        SpringParityRule rule = new SpringParityRule();

        List<String> violations = rule.checkGeneratorCompleteness(reactor);
        assertThat(violations).hasSize(1);
        assertThat(violations.get(0)).contains("governance-spring");
        assertThat(violations.get(0)).contains("no 'verify' goal");
    }

    @Test
    void generatorWithBothGoals_passes() {
        MavenProject governanceSpring = createProject("governance-spring");
        addGeneratorPlugin(governanceSpring, "casehub-platform-spring-generator",
                tempDir.resolve("governance").toAbsolutePath().toString(), true);

        List<MavenProject> reactor = List.of(governanceSpring);
        SpringParityRule rule = new SpringParityRule();

        List<String> violations = rule.checkGeneratorCompleteness(reactor);
        assertThat(violations).isEmpty();
    }

    @Test
    void handWrittenSpringModule_noGeneratorCheck() {
        MavenProject callbackSpring = createProject("callback-spring");

        List<MavenProject> reactor = List.of(callbackSpring);
        SpringParityRule rule = new SpringParityRule();

        List<String> violations = rule.checkGeneratorCompleteness(reactor);
        assertThat(violations).isEmpty();
    }

    @Test
    void multipleQuarkusModules_allCovered() {
        MavenProject platformSpring = createProject("platform-spring");
        Build build = platformSpring.getModel().getBuild();

        Plugin restGen = new Plugin();
        restGen.setGroupId("io.casehub");
        restGen.setArtifactId("casehub-platform-rest-spring-generator");

        PluginExecution gen = new PluginExecution();
        gen.setId("generate");
        gen.addGoal("generate");
        Xpp3Dom config = new Xpp3Dom("configuration");
        Xpp3Dom modules = new Xpp3Dom("quarkusModules");
        Xpp3Dom mod1 = new Xpp3Dom("quarkusModule");
        mod1.setValue(tempDir.resolve("streams-webhook").toAbsolutePath().toString());
        modules.addChild(mod1);
        Xpp3Dom mod2 = new Xpp3Dom("quarkusModule");
        mod2.setValue(tempDir.resolve("callback-client").toAbsolutePath().toString());
        modules.addChild(mod2);
        config.addChild(modules);
        gen.setConfiguration(config);
        restGen.addExecution(gen);

        PluginExecution verify = new PluginExecution();
        verify.setId("verify-drift");
        verify.addGoal("verify");
        restGen.addExecution(verify);

        build.addPlugin(restGen);

        MavenProject webhook = createProject("streams-webhook");
        MavenProject callbackClient = createProject("callback-client");

        List<MavenProject> reactor = List.of(platformSpring, webhook, callbackClient);
        SpringParityRule rule = new SpringParityRule();

        Set<String> covered = rule.buildCoveredSet(reactor);
        assertThat(covered).contains(
                "casehub-platform-streams-webhook",
                "casehub-platform-callback-client");
    }

    @Test
    void pomPackaging_excluded() {
        MavenProject parent = createProject("platform-parent");
        parent.getModel().setPackaging("pom");

        List<MavenProject> reactor = List.of(parent);
        SpringParityRule rule = new SpringParityRule();

        Set<String> candidates = rule.buildCandidateSet(reactor);
        assertThat(candidates).isEmpty();
    }
}
