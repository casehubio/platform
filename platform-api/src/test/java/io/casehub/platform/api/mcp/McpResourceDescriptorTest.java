package io.casehub.platform.api.mcp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class McpResourceDescriptorTest {

    @Test
    void staticFactoryCreatesStaticDescriptor() {
        var desc = McpResourceDescriptor.of("idx", "casehub://index", "application/json", "Index");
        assertThat(desc).isInstanceOf(StaticResourceDescriptor.class);
        assertThat(desc.name()).isEqualTo("idx");
        assertThat(desc.uri()).isEqualTo("casehub://index");
        assertThat(desc.mimeType()).isEqualTo("application/json");
        assertThat(desc.description()).isEqualTo("Index");
        assertThat(desc.subscribable()).isFalse();
    }

    @Test
    void templateFactoryCreatesTemplateDescriptor() {
        var desc = McpResourceDescriptor.template("tpl", "iot://devices/{id}", "application/json", "Device");
        assertThat(desc).isInstanceOf(TemplateResourceDescriptor.class);
        assertThat(desc.name()).isEqualTo("tpl");
        assertThat(((TemplateResourceDescriptor) desc).uriTemplate()).isEqualTo("iot://devices/{id}");
        assertThat(desc.subscribable()).isFalse();
    }

    @Test
    void staticWithSubscribableReturnsNewInstance() {
        var desc = McpResourceDescriptor.of("idx", "casehub://index", "application/json", "Index");
        var sub = desc.withSubscribable(true);
        assertThat(sub.subscribable()).isTrue();
        assertThat(desc.subscribable()).isFalse();
    }

    @Test
    void templateWithSubscribableReturnsNewInstance() {
        var desc = McpResourceDescriptor.template("tpl", "iot://d/{id}", null, "Device");
        var sub = desc.withSubscribable(true);
        assertThat(sub.subscribable()).isTrue();
    }

    @Test
    void staticNullNameThrows() {
        assertThatNullPointerException()
                .isThrownBy(() -> McpResourceDescriptor.of(null, "uri", "mime", "desc"));
    }

    @Test
    void staticNullUriThrows() {
        assertThatNullPointerException()
                .isThrownBy(() -> McpResourceDescriptor.of("n", null, "mime", "desc"));
    }

    @Test
    void templateNullUriTemplateThrows() {
        assertThatNullPointerException()
                .isThrownBy(() -> McpResourceDescriptor.template("n", null, "mime", "desc"));
    }

    @Test
    void nullMimeTypeIsAllowed() {
        var desc = McpResourceDescriptor.of("idx", "casehub://index", null, "Index");
        assertThat(desc.mimeType()).isNull();
    }

    @Test
    void sealedHierarchyExhaustive() {
        McpResourceDescriptor desc = McpResourceDescriptor.of("n", "u", null, "d");
        String result = switch (desc) {
            case StaticResourceDescriptor s -> s.uri();
            case TemplateResourceDescriptor t -> t.uriTemplate();
        };
        assertThat(result).isEqualTo("u");
    }
}
