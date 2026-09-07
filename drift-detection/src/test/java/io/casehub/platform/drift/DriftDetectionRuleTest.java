package io.casehub.platform.drift;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;

class DriftDetectionRuleTest {

    @TempDir Path tempDir;

    private Path createDir(String name) throws IOException {
        Path dir = tempDir.resolve(name);
        Files.createDirectories(dir);
        return dir;
    }

    private void createJavaFile(Path dir, String className) throws IOException {
        Files.writeString(dir.resolve(className + ".java"), "public class " + className + " {}");
    }

    @Test
    void noDrift_generatedAndHandWrittenMatch() throws Exception {
        Path generated = createDir("generated");
        Path source = createDir("src/io/casehub/model");
        createJavaFile(generated, "Foo");
        createJavaFile(source, "Foo");

        DriftDetectionRule rule = new DriftDetectionRule();
        assertThatNoException().isThrownBy(() ->
            rule.detectDrift(generated.toString(), source.toString(), null));
    }

    @Test
    void driftDetected_handWrittenNotInGenerated() throws Exception {
        Path generated = createDir("generated");
        Path source = createDir("src/io/casehub/model");
        createJavaFile(generated, "Foo");
        createJavaFile(source, "Foo");
        createJavaFile(source, "Bar");

        DriftDetectionRule rule = new DriftDetectionRule();
        assertThatThrownBy(() ->
            rule.detectDrift(generated.toString(), source.toString(), null))
            .isInstanceOf(EnforcerRuleException.class)
            .hasMessageContaining("Bar");
    }

    @Test
    void allowList_permitsHandWrittenType() throws Exception {
        Path generated = createDir("generated");
        Path source = createDir("src/io/casehub/model");
        createJavaFile(generated, "Foo");
        createJavaFile(source, "Foo");
        createJavaFile(source, "Bar");

        Path allowList = tempDir.resolve("exceptions.txt");
        Files.writeString(allowList, "# Manually written\nBar\n");

        DriftDetectionRule rule = new DriftDetectionRule();
        assertThatNoException().isThrownBy(() ->
            rule.detectDrift(generated.toString(), source.toString(), allowList.toString()));
    }

    @Test
    void allowListJustification_parsesCorrectly() throws Exception {
        Path allowList = tempDir.resolve("exceptions.txt");
        Files.writeString(allowList, "# Has justification\nJustified\nUnjustified\n");

        DriftDetectionRule.AllowList parsed = DriftDetectionRule.loadAllowList(allowList.toString());
        assertThat(parsed.entries()).containsExactlyInAnyOrder("Justified", "Unjustified");
        assertThat(parsed.unjustified()).containsExactly("Unjustified");
    }

    @Test
    void emptyGeneratedDir_allHandWrittenFlagged() throws Exception {
        Path generated = createDir("generated");
        Path source = createDir("src/io/casehub/model");
        createJavaFile(source, "Foo");

        DriftDetectionRule rule = new DriftDetectionRule();
        assertThatThrownBy(() ->
            rule.detectDrift(generated.toString(), source.toString(), null))
            .isInstanceOf(EnforcerRuleException.class)
            .hasMessageContaining("Foo");
    }

    @Test
    void missingAllowListFile_treatedAsEmpty() throws Exception {
        Path generated = createDir("generated");
        Path source = createDir("src/io/casehub/model");
        createJavaFile(generated, "Foo");
        createJavaFile(source, "Foo");
        createJavaFile(source, "Bar");

        DriftDetectionRule rule = new DriftDetectionRule();
        assertThatThrownBy(() ->
            rule.detectDrift(generated.toString(), source.toString(),
                tempDir.resolve("nonexistent.txt").toString()))
            .isInstanceOf(EnforcerRuleException.class)
            .hasMessageContaining("Bar");
    }

    @Test
    void packageInfoFiles_excluded() throws Exception {
        Path generated = createDir("generated");
        Path source = createDir("src/io/casehub/model");
        createJavaFile(generated, "Foo");
        createJavaFile(source, "Foo");
        createJavaFile(source, "package-info");

        DriftDetectionRule rule = new DriftDetectionRule();
        assertThatNoException().isThrownBy(() ->
            rule.detectDrift(generated.toString(), source.toString(), null));
    }
}
