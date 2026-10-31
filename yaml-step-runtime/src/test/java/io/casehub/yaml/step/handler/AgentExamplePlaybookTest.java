package io.casehub.yaml.step.handler;

import io.casehub.platform.compiler.CompilationOutcome;
import io.casehub.platform.compiler.PlaybookCompiler;
import io.casehub.yaml.core.playbook.PlaybookDocument;
import io.casehub.yaml.step.statemachine.PlaybookParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AgentExamplePlaybookTest {

    private static final Path EXAMPLES_DIR = resolveExamplesDir();

    private static Path resolveExamplesDir() {
        Path candidate = Path.of("examples/agent-interaction-patterns");
        if (Files.isDirectory(candidate)) return candidate;
        candidate = Path.of("../examples/agent-interaction-patterns");
        if (Files.isDirectory(candidate)) return candidate;
        return Path.of("examples/agent-interaction-patterns");
    }

    private PlaybookCompiler compiler;

    @BeforeEach
    void setUp() {
        compiler = new PlaybookCompiler();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "01-direct-invoke.yaml",
            "02-session-conversation.yaml",
            "03-session-with-clear.yaml",
            "04-parallel-queries.yaml",
            "05-pool-backed.yaml",
            "06-multi-channel.yaml",
            "07-salience-priority.yaml",
            "08-structured-output.yaml"
    })
    void example_compilesSuccessfully(String filename) throws IOException {
        String yaml = Files.readString(EXAMPLES_DIR.resolve(filename));
        PlaybookDocument doc = PlaybookParser.parse(yaml);
        CompilationOutcome outcome = compiler.compile(doc);

        assertThat(outcome)
                .as("Example %s should compile without errors", filename)
                .isInstanceOf(CompilationOutcome.Success.class);

        var result = ((CompilationOutcome.Success) outcome).result();
        assertThat(result.workflow().getDo())
                .as("Example %s should produce at least one task", filename)
                .isNotEmpty();
    }

    @Test
    void example01_producesThreeSteps() throws IOException {
        var result = compileExample("01-direct-invoke.yaml");
        assertThat(result.workflow().getDo()).hasSize(3);
    }

    @Test
    void example02_producesFiveSteps() throws IOException {
        var result = compileExample("02-session-conversation.yaml");
        assertThat(result.workflow().getDo()).hasSize(5);
    }

    @Test
    void example03_includesClearStep() throws IOException {
        var result = compileExample("03-session-with-clear.yaml");
        boolean hasClear = result.workflow().getDo().stream()
                .anyMatch(item -> item.getName().startsWith("agent-session-clear"));
        assertThat(hasClear).as("Should contain an agent-session-clear step").isTrue();
    }

    @Test
    void example04_includesParallelBlock() throws IOException {
        var result = compileExample("04-parallel-queries.yaml");
        boolean hasParallel = result.workflow().getDo().stream()
                .anyMatch(item -> item.getTask().getForkTask() != null);
        assertThat(hasParallel).as("Should contain a parallel (fork) task").isTrue();
    }

    @Test
    void example08_includesPriorityQuery() throws IOException {
        String yaml = Files.readString(EXAMPLES_DIR.resolve("08-structured-output.yaml"));
        assertThat(yaml).contains("priority: true");
    }

    @Test
    void example05_parsesInlinePool() throws IOException {
        var result = compileExample("05-pool-backed.yaml");
        assertThat(result.poolDeclarations()).hasSize(1);
        assertThat(result.poolDeclarations().getFirst().name()).isEqualTo("analysts");
        assertThat(result.poolDeclarations().getFirst().clearing())
                .containsEntry("policy", "size-aware");
    }

    @Test
    void example07_parsesThreePoolsWithSalience() throws IOException {
        var result = compileExample("07-salience-priority.yaml");
        assertThat(result.poolDeclarations()).hasSize(3);
        var voice = result.poolDeclarations().stream()
                          .filter(p -> "voice".equals(p.name())).findFirst().orElseThrow();
        assertThat(voice.salience()).isEqualTo(2);
        var email = result.poolDeclarations().stream()
                          .filter(p -> "email".equals(p.name())).findFirst().orElseThrow();
        assertThat(email.salience()).isZero();
    }


    private io.casehub.platform.compiler.CompilationResult compileExample(String filename) throws IOException {
        String yaml = Files.readString(EXAMPLES_DIR.resolve(filename));
        PlaybookDocument doc = PlaybookParser.parse(yaml);
        CompilationOutcome outcome = compiler.compile(doc);
        assertThat(outcome).isInstanceOf(CompilationOutcome.Success.class);
        return ((CompilationOutcome.Success) outcome).result();
    }
}
