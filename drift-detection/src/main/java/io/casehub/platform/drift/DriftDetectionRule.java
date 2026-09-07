package io.casehub.platform.drift;

import org.apache.maven.enforcer.rule.api.EnforcerRule;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.apache.maven.enforcer.rule.api.EnforcerRuleHelper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DriftDetectionRule implements EnforcerRule {

    private String generatedSourcesDir;
    private String sourceRoot;
    private String targetPackage;
    private String allowListFile;

    @Override
    public void execute(EnforcerRuleHelper helper) throws EnforcerRuleException {
        if (generatedSourcesDir == null || generatedSourcesDir.isBlank()) {
            throw new EnforcerRuleException(
                    "driftDetectionRule requires <generatedSourcesDir> configuration");
        }
        if (targetPackage == null || targetPackage.isBlank()) {
            throw new EnforcerRuleException(
                    "driftDetectionRule requires <targetPackage> configuration");
        }

        String resolvedSourceDir = sourceRoot != null ? sourceRoot : "src/main/java";
        String packagePath       = targetPackage.replace('.', '/');
        String fullSourcePath    = resolvedSourceDir + "/" + packagePath;

        List<String> warnings = new ArrayList<>();
        try {
            detectDrift(generatedSourcesDir, fullSourcePath, allowListFile, warnings);
            for (String w : warnings) {
                helper.getLog().warn(w);
            }
        } catch (IOException e) {
            throw new EnforcerRuleException("Drift detection failed: " + e.getMessage(), e);
        }
    }

    void detectDrift(String generatedDir, String sourceDir, String allowListPath)
            throws IOException, EnforcerRuleException {
        detectDrift(generatedDir, sourceDir, allowListPath, new ArrayList<>());
    }

    void detectDrift(String generatedDir, String sourceDir, String allowListPath,
            List<String> warnings) throws IOException, EnforcerRuleException {
        Set<String> generated = scanJavaTypes(Path.of(generatedDir));
        Set<String> handWritten = scanJavaTypes(Path.of(sourceDir));
        AllowList allowList = loadAllowList(allowListPath);

        for (String entry : allowList.unjustified()) {
            warnings.add("Allow-list entry '" + entry + "' has no justification comment");
        }

        Set<String> drift = new TreeSet<>(handWritten);
        drift.removeAll(generated);
        drift.removeAll(allowList.entries());

        if (!drift.isEmpty()) {
            throw new EnforcerRuleException(
                "Codegen drift detected — hand-written types in " + sourceDir
                + " not in generated output or allow-list:\n"
                + drift.stream().map(t -> "  - " + t).collect(Collectors.joining("\n"))
                + "\n\nTo allow intentionally hand-written types, add them to "
                + (allowListPath != null ? allowListPath : "<allow-list-file>")
                + " with a justification comment.");
        }
    }

    private Set<String> scanJavaTypes(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return Set.of();
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files
                .filter(p -> p.toString().endsWith(".java"))
                .map(p -> p.getFileName().toString())
                .map(name -> name.substring(0, name.length() - 5))
                .filter(name -> !name.equals("package-info"))
                .collect(Collectors.toCollection(TreeSet::new));
        }
    }

    static AllowList loadAllowList(String path) throws IOException {
        if (path == null) {
            return new AllowList(Set.of(), Set.of());
        }
        Path file = Path.of(path);
        if (!Files.exists(file)) {
            return new AllowList(Set.of(), Set.of());
        }
        Set<String> entries = new TreeSet<>();
        Set<String> unjustified = new TreeSet<>();
        boolean previousWasComment = false;

        for (String line : Files.readAllLines(file)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                previousWasComment = false;
                continue;
            }
            if (trimmed.startsWith("#")) {
                previousWasComment = true;
                continue;
            }
            entries.add(trimmed);
            if (!previousWasComment) {
                unjustified.add(trimmed);
            }
            previousWasComment = false;
        }
        return new AllowList(entries, unjustified);
    }

    record AllowList(Set<String> entries, Set<String> unjustified) {}

    @Override
    public boolean isCacheable() {
        return false;
    }

    @Override
    public boolean isResultValid(EnforcerRule cachedRule) {
        return false;
    }

    @Override
    public String getCacheId() {
        return null;
    }
}
