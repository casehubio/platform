package io.casehub.platform.drift;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.maven.enforcer.rule.api.AbstractEnforcerRule;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Named("springParityRule")
public class SpringParityRule extends AbstractEnforcerRule {

    private static final Set<String> GENERATOR_ARTIFACTS = Set.of(
            "casehub-platform-spring-generator",
            "casehub-platform-graphql-spring-generator",
            "casehub-platform-rest-spring-generator",
            "casehub-platform-mcp-spring-generator");

    private static final List<String> AUTO_EXCLUDE_SUFFIXES = List.of(
            "-core", "-api", "-spring", "-spring-jpa",
            "-jpa-common", "-jpa", "-testing",
            "-generator", "-starter", "-alpha");

    private final MavenSession session;

    private String exceptionsFile;

    @Inject
    public SpringParityRule(MavenSession session) {
        this.session = session;
    }

    SpringParityRule() {
        this.session = null;
    }

    @Override
    public void execute() throws EnforcerRuleException {
        List<MavenProject> reactor = session.getProjects();
        DriftDetectionRule.AllowList allowList;
        try {
            allowList = DriftDetectionRule.loadAllowList(exceptionsFile);
        } catch (IOException e) {
            throw new EnforcerRuleException("Failed to load exceptions file: " + e.getMessage(), e);
        }

        List<String> warnings = new ArrayList<>();
        for (String entry : allowList.unjustified()) {
            warnings.add("Exception entry '" + entry + "' has no justification comment");
        }
        for (String w : warnings) {
            getLog().warn(w);
        }

        Set<String> covered = buildCoveredSet(reactor);
        Set<String> candidates = buildCandidateSet(reactor);

        List<String> coverageViolations = checkModuleCoverage(candidates, covered, allowList);
        List<String> generatorViolations = checkGeneratorCompleteness(reactor);

        List<String> allViolations = new ArrayList<>(coverageViolations);
        allViolations.addAll(generatorViolations);

        if (!allViolations.isEmpty()) {
            throw new EnforcerRuleException(
                    "Spring parity violations:\n"
                    + allViolations.stream().map(v -> "  - " + v).collect(Collectors.joining("\n")));
        }
    }

    Set<String> buildCoveredSet(List<MavenProject> reactor) {
        Set<String> covered = new TreeSet<>();

        for (MavenProject project : reactor) {
            String artifactId = project.getArtifactId();
            if (!artifactId.endsWith("-spring")) {
                continue;
            }

            boolean hasGenerators = false;

            if (project.getModel().getBuild() != null) {
                for (Plugin plugin : project.getModel().getBuild().getPlugins()) {
                    if (!GENERATOR_ARTIFACTS.contains(plugin.getArtifactId())) {
                        continue;
                    }
                    hasGenerators = true;

                    for (PluginExecution exec : plugin.getExecutions()) {
                        Object config = exec.getConfiguration();
                        if (config instanceof Xpp3Dom dom) {
                            extractQuarkusModules(dom, project, reactor, covered);
                        }
                    }
                    Object pluginConfig = plugin.getConfiguration();
                    if (pluginConfig instanceof Xpp3Dom dom) {
                        extractQuarkusModules(dom, project, reactor, covered);
                    }
                }
            }

            if (!hasGenerators) {
                String baseModule = artifactId.substring(0,
                        artifactId.length() - "-spring".length());
                covered.add(baseModule);
            }
        }

        return covered;
    }

    private void extractQuarkusModules(Xpp3Dom config, MavenProject springProject,
                                        List<MavenProject> reactor, Set<String> covered) {
        Xpp3Dom single = config.getChild("quarkusModule");
        if (single != null && single.getValue() != null) {
            resolveToReactorModule(single.getValue(), springProject, reactor, covered);
        }

        Xpp3Dom multi = config.getChild("quarkusModules");
        if (multi != null) {
            for (Xpp3Dom child : multi.getChildren("quarkusModule")) {
                if (child.getValue() != null) {
                    resolveToReactorModule(child.getValue(), springProject, reactor, covered);
                }
            }
        }
    }

    private void resolveToReactorModule(String path, MavenProject springProject,
                                         List<MavenProject> reactor, Set<String> covered) {
        String resolved = path.replace("${project.basedir}",
                springProject.getBasedir().getAbsolutePath());
        Path resolvedPath = Path.of(resolved).normalize();

        for (MavenProject candidate : reactor) {
            if (candidate.getBasedir() != null
                    && candidate.getBasedir().toPath().normalize().equals(resolvedPath)) {
                covered.add(candidate.getArtifactId());
                return;
            }
        }

        String dirName = resolvedPath.getFileName().toString();
        for (MavenProject candidate : reactor) {
            if (candidate.getBasedir() != null
                    && candidate.getBasedir().getName().equals(dirName)) {
                covered.add(candidate.getArtifactId());
                return;
            }
        }
    }

    Set<String> buildCandidateSet(List<MavenProject> reactor) {
        Set<String> candidates = new TreeSet<>();
        for (MavenProject project : reactor) {
            String artifactId = project.getArtifactId();

            if ("pom".equals(project.getModel().getPackaging())) {
                continue;
            }

            boolean excluded = AUTO_EXCLUDE_SUFFIXES.stream()
                    .anyMatch(artifactId::endsWith);
            if (excluded) {
                continue;
            }

            candidates.add(artifactId);
        }
        return candidates;
    }

    List<String> checkModuleCoverage(Set<String> candidates, Set<String> covered,
                                      DriftDetectionRule.AllowList allowList) {
        List<String> violations = new ArrayList<>();
        for (String candidate : candidates) {
            if (covered.contains(candidate)) {
                continue;
            }
            String shortName = stripPrefix(candidate);
            if (allowList.entries().contains(shortName)
                    || allowList.entries().contains(candidate)) {
                continue;
            }
            violations.add("Module '" + shortName + "' has no Spring coverage — "
                    + "add a *-spring module, a <quarkusModule> reference, "
                    + "or list it in the exceptions file with justification.");
        }
        return violations;
    }

    List<String> checkGeneratorCompleteness(List<MavenProject> reactor) {
        List<String> violations = new ArrayList<>();

        for (MavenProject project : reactor) {
            String artifactId = project.getArtifactId();
            if (!artifactId.endsWith("-spring")) {
                continue;
            }
            if (project.getModel().getBuild() == null) {
                continue;
            }

            for (Plugin plugin : project.getModel().getBuild().getPlugins()) {
                if (!GENERATOR_ARTIFACTS.contains(plugin.getArtifactId())) {
                    continue;
                }

                boolean hasGenerate = false;
                boolean hasVerify = false;
                for (PluginExecution exec : plugin.getExecutions()) {
                    if (exec.getGoals().contains("generate")) {
                        hasGenerate = true;
                    }
                    if (exec.getGoals().contains("verify")) {
                        hasVerify = true;
                    }
                }

                if (hasGenerate && !hasVerify) {
                    String shortName = stripPrefix(artifactId);
                    String generatorShort = stripPrefix(plugin.getArtifactId());
                    violations.add("Module '" + shortName + "' has " + generatorShort
                            + " with 'generate' goal but no 'verify' goal"
                            + " — drift will go undetected.");
                }
            }
        }

        return violations;
    }

    private String stripPrefix(String artifactId) {
        return artifactId.startsWith("casehub-platform-")
                ? artifactId.substring("casehub-platform-".length())
                : artifactId;
    }
}
