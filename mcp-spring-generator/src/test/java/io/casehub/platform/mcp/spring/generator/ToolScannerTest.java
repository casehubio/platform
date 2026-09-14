package io.casehub.platform.mcp.spring.generator;

import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ToolScannerTest {

    private static Index index;

    @BeforeAll
    static void buildIndex() throws Exception {
        var indexer = new Indexer();
        indexer.indexClass(SampleTools.class);
        index = indexer.complete();
    }

    @Test
    void scansToolAnnotatedMethods() {
        var scanner = new ToolScanner();
        List<ToolDescriptor> tools = scanner.scan(index);

        assertThat(tools).hasSize(2);
    }

    @Test
    void extractsToolDescription() {
        var scanner = new ToolScanner();
        List<ToolDescriptor> tools = scanner.scan(index);

        ToolDescriptor listSessions = tools.stream()
                .filter(t -> t.methodName().equals("listSessions")).findFirst().orElseThrow();
        assertThat(listSessions.description()).isEqualTo("List available sessions");
    }

    @Test
    void extractsParameters() {
        var scanner = new ToolScanner();
        List<ToolDescriptor> tools = scanner.scan(index);

        ToolDescriptor createSession = tools.stream()
                .filter(t -> t.methodName().equals("createSession")).findFirst().orElseThrow();
        assertThat(createSession.parameters()).hasSize(2);
        assertThat(createSession.parameters().get(0).description()).isEqualTo("Session name");
    }

    @Test
    void skipsNonToolMethods() {
        var scanner = new ToolScanner();
        List<ToolDescriptor> tools = scanner.scan(index);

        assertThat(tools).noneMatch(t -> t.methodName().equals("helperMethod"));
    }
}
