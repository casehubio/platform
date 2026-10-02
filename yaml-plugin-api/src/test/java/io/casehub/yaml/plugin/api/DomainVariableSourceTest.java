package io.casehub.yaml.plugin.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DomainVariableSourceTest {

    @Test
    void interface_contract_returns_prefix_and_resolves_path() {
        DomainVariableSource source = new DomainVariableSource() {
            @Override public String prefix() { return "test"; }
            @Override public Object resolve(String path) {
                return "value".equals(path) ? 42 : null;
            }
        };

        assertThat(source.prefix()).isEqualTo("test");
        assertThat(source.resolve("value")).isEqualTo(42);
        assertThat(source.resolve("missing")).isNull();
    }
}
