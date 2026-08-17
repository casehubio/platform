package io.casehub.platform.api.mcp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class McpDomainTest {

    @McpDomain("engine")
    static class AnnotatedClass {}

    static class UnannotatedClass {}

    @Test
    void annotationRetainedAtRuntime() {
        McpDomain ann = AnnotatedClass.class.getAnnotation(McpDomain.class);
        assertThat(ann).isNotNull();
        assertThat(ann.value()).isEqualTo("engine");
    }

    @Test
    void absentWhenNotAnnotated() {
        assertThat(UnannotatedClass.class.getAnnotation(McpDomain.class)).isNull();
    }
}
