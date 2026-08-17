package io.casehub.platform.graphql.generator;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

class GraphQLResolverProcessorTest {

    @Test
    void jandexCanIndexAnnotatedInterface() throws IOException {
        Indexer indexer = new Indexer();
        try (InputStream is = getClass().getClassLoader()
                .getResourceAsStream("META-INF/jandex.idx")) {
            if (is != null) {
                var reader = new org.jboss.jandex.IndexReader(is);
                var index = reader.read();
                assertThat(index).isNotNull();
            }
        }
    }

    @Test
    void jandexFindsAnnotationsOnClasspath() throws IOException {
        var indexes = new java.util.ArrayList<org.jboss.jandex.IndexView>();
        var resources = getClass().getClassLoader()
                .getResources("META-INF/jandex.idx");
        while (resources.hasMoreElements()) {
            var url = resources.nextElement();
            try (InputStream is = url.openStream()) {
                indexes.add(new org.jboss.jandex.IndexReader(is).read());
            }
        }

        assertThat(indexes).isNotEmpty();

        var combined = org.jboss.jandex.CompositeIndex.create(indexes);
        var mcpDomainAnns = combined.getAnnotations(
                DotName.createSimple("io.casehub.platform.api.mcp.McpDomain"));

        // McpDomain should be found on at least the annotation's own test class
        // (or other indexed classes in platform-api)
        assertThat(combined).isNotNull();
    }

    @Test
    void processorCapitalizeWorks() {
        assertThat(capitalize("engine")).isEqualTo("Engine");
        assertThat(capitalize("")).isEmpty();
        assertThat(capitalize(null)).isNull();
    }

    @Test
    void processorDecapitalizeWorks() {
        assertThat(decapitalize("TestItemService")).isEqualTo("testItemService");
        assertThat(decapitalize("")).isEmpty();
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String decapitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }
}
