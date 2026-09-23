package io.casehub.platform.spring.generator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ManualBeanScannerTest {

    @TempDir
    Path tempDir;

    @Test
    void findsReturnTypesFromBeanMethods() throws IOException {
        Path javaFile = tempDir.resolve("CommonManualConfig.java");
        Files.writeString(javaFile, """
                package io.casehub.engine.common.spring;

                import org.springframework.context.annotation.Bean;
                import org.springframework.context.annotation.Configuration;

                @Configuration
                public class CommonManualConfig {

                    @Bean
                    public BridgeResolver bridgeResolver(List<ContextBridge<?>> bridges) {
                        return new BridgeResolver(bridges);
                    }

                    @Bean
                    public JudgmentNodeExecutor judgmentNodeExecutor(Optional<JudgmentScheduler> scheduler) {
                        return new JudgmentNodeExecutor(scheduler);
                    }
                }
                """);

        Set<String> types = new ManualBeanScanner().scan(tempDir);

        assertThat(types).containsExactlyInAnyOrder(
                "io.casehub.engine.common.spring.BridgeResolver",
                "io.casehub.engine.common.spring.JudgmentNodeExecutor");
    }

    @Test
    void ignoresFilesWithoutBeanAnnotation() throws IOException {
        Path javaFile = tempDir.resolve("SomeUtil.java");
        Files.writeString(javaFile, """
                package io.casehub.engine.common.spring;

                public class SomeUtil {
                    public String helper() { return "ok"; }
                }
                """);

        Set<String> types = new ManualBeanScanner().scan(tempDir);

        assertThat(types).isEmpty();
    }

    @Test
    void handlesNonExistentDirectory() throws IOException {
        Path missing = tempDir.resolve("nonexistent");

        Set<String> types = new ManualBeanScanner().scan(missing);

        assertThat(types).isEmpty();
    }

    @Test
    void scansRecursively() throws IOException {
        Path subDir = tempDir.resolve("sub/package");
        Files.createDirectories(subDir);
        Path javaFile = subDir.resolve("DeepConfig.java");
        Files.writeString(javaFile, """
                package io.casehub.engine.common.spring.sub;

                import org.springframework.context.annotation.Bean;

                public class DeepConfig {
                    @Bean
                    public DataRefRegistry dataRefRegistry() {
                        return new DataRefRegistry();
                    }
                }
                """);

        Set<String> types = new ManualBeanScanner().scan(tempDir);

        assertThat(types).containsExactly("io.casehub.engine.common.spring.sub.DataRefRegistry");
    }
}
