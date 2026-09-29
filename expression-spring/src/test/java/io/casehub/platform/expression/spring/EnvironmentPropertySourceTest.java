package io.casehub.platform.expression.spring;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class EnvironmentPropertySourceTest {

    @Test
    void getProperty_returns_value() {
        var env = new MockEnvironment().withProperty("app.name", "test");
        var source = new EnvironmentPropertySource(env);
        assertThat(source.getProperty("app.name")).hasValue("test");
    }

    @Test
    void getProperty_returns_empty_when_missing() {
        var env = new MockEnvironment();
        var source = new EnvironmentPropertySource(env);
        assertThat(source.getProperty("missing")).isEmpty();
    }

    @Test
    void getPropertyNames_returns_all_names() {
        var env = new MockEnvironment()
                .withProperty("a", "1")
                .withProperty("b", "2");
        var source = new EnvironmentPropertySource(env);
        List<String> names = new ArrayList<>();
        source.getPropertyNames().forEach(names::add);
        assertThat(names).contains("a", "b");
    }
}
