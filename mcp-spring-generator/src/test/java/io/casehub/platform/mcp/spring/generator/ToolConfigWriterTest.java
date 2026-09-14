package io.casehub.platform.mcp.spring.generator;

import com.palantir.javapoet.JavaFile;
import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ToolConfigWriterTest {

    private static List<ToolDescriptor> tools;

    @BeforeAll
    static void scan() throws Exception {
        var indexer = new Indexer();
        indexer.indexClass(SampleTools.class);
        Index index = indexer.complete();

        var scanner = new ToolScanner();
        tools = scanner.scan(index);
    }

    @Test
    void generatesConfigurationClass() {
        var writer = new ToolConfigWriter();
        JavaFile javaFile = writer.generate(
                "io.casehub.platform.mcp.spring.generator.SampleTools",
                tools, "test.spring");
        String source = javaFile.toString();

        assertThat(source).contains("@Configuration");
        assertThat(source).contains("class SpringSampleTools");
    }

    @Test
    void generatesToolAnnotations() {
        var writer = new ToolConfigWriter();
        String source = writer.generate(
                "io.casehub.platform.mcp.spring.generator.SampleTools",
                tools, "test.spring").toString();

        assertThat(source).contains("@Tool(\"List available sessions\")");
        assertThat(source).contains("@Tool(\"Create a new session\")");
    }

    @Test
    void generatesToolParamAnnotations() {
        var writer = new ToolConfigWriter();
        String source = writer.generate(
                "io.casehub.platform.mcp.spring.generator.SampleTools",
                tools, "test.spring").toString();

        assertThat(source).contains("@ToolParam(description = \"Tenant ID\")");
    }

    @Test
    void delegatesToOriginalClass() {
        var writer = new ToolConfigWriter();
        String source = writer.generate(
                "io.casehub.platform.mcp.spring.generator.SampleTools",
                tools, "test.spring").toString();

        assertThat(source).contains("SampleTools");
        assertThat(source).contains("this.sampleTools =");
        assertThat(source).contains("sampleTools.listSessions(");
    }
}
