package io.casehub.yaml.core.resolver;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ObjectVariableSourceTest {

    @Test
    void soleReference_returnsTypedObject() {
        Map<String, Object> stepResult = Map.of("price", 42.5, "symbol", "AAPL");
        ObjectVariableSource source = name -> {
            if ("myStep".equals(name)) return stepResult;
            return null;
        };
        VariableResolver resolver = new VariableResolver(Map.of(), Set.of())
                .withObjectScope("result", source);

        Object resolved = resolver.resolve("${result.myStep}");
        assertThat(resolved).isEqualTo(stepResult);
    }

    @Test
    void soleReference_drillsNestedFields() {
        Map<String, Object> stepResult = Map.of("price", 42.5, "symbol", "AAPL");
        ObjectVariableSource source = name -> {
            if ("myStep".equals(name)) return stepResult;
            return null;
        };
        VariableResolver resolver = new VariableResolver(Map.of(), Set.of())
                .withObjectScope("result", source);

        Object resolved = resolver.resolve("${result.myStep.price}");
        assertThat(resolved).isEqualTo(42.5);
    }

    @Test
    void interpolation_fallsBackToStringSource() {
        ObjectVariableSource objSource = name -> {
            if ("myStep".equals(name)) return Map.of("price", 42.5);
            return null;
        };
        VariableSource strSource = name -> {
            if ("myStep.price".equals(name)) return "42.5";
            return null;
        };
        VariableResolver resolver = new VariableResolver(Map.of("result", strSource), Set.of())
                .withObjectScope("result", objSource);

        Object resolved = resolver.resolve("price is ${result.myStep.price} USD");
        assertThat(resolved).isEqualTo("price is 42.5 USD");
    }

    @Test
    void soleReference_noObjectSource_fallsBackToStringSource() {
        VariableSource strSource = name -> {
            if ("name".equals(name)) return "hello";
            return null;
        };
        VariableResolver resolver = new VariableResolver(Map.of("var", strSource), Set.of());

        Object resolved = resolver.resolve("${var.name}");
        assertThat(resolved).isEqualTo("hello");
    }

    @Test
    void objectSource_returnsNull_fallsBackToStringSource() {
        ObjectVariableSource objSource = name -> null;
        VariableSource strSource = name -> {
            if ("myStep.field".equals(name)) return "fallback";
            return null;
        };
        VariableResolver resolver = new VariableResolver(Map.of("result", strSource), Set.of())
                .withObjectScope("result", objSource);

        Object resolved = resolver.resolve("${result.myStep.field}");
        assertThat(resolved).isEqualTo("fallback");
    }

    @Test
    void nestedFieldDrilling_mapHierarchy() {
        Map<String, Object> deep = Map.of(
                "level1", Map.of(
                        "level2", Map.of(
                                "value", "found")));
        ObjectVariableSource source = name -> {
            if ("step".equals(name)) return deep;
            return null;
        };
        VariableResolver resolver = new VariableResolver(Map.of(), Set.of())
                .withObjectScope("result", source);

        Object resolved = resolver.resolve("${result.step.level1.level2.value}");
        assertThat(resolved).isEqualTo("found");
    }

    @Test
    void errorMapResolution() {
        Map<String, Object> errorMap = Map.of(
                "message", "Connection refused",
                "exceptionClass", "java.net.ConnectException");
        Map<String, Object> stepResult = Map.of("error", errorMap, "data", "ok");
        ObjectVariableSource source = name -> {
            if ("step".equals(name)) return stepResult;
            return null;
        };
        VariableResolver resolver = new VariableResolver(Map.of(), Set.of())
                .withObjectScope("result", source);

        Object resolved = resolver.resolve("${result.step.error}");
        assertThat(resolved).isEqualTo(errorMap);

        Object message = resolver.resolve("${result.step.error.message}");
        assertThat(message).isEqualTo("Connection refused");
    }

    @Test
    void drillOnly_bareMapReference_fallsThroughToString() {
        Map<String, Object> row = Map.of("name", "Alice", "port", 8080);
        ObjectVariableSource objSource = ObjectVariableSource.drillOnly(
                name -> "member".equals(name) ? row : null);
        VariableSource strSource = name -> "member".equals(name) ? "Alice" : null;
        VariableResolver resolver = new VariableResolver(Map.of("each", strSource), Set.of())
                                            .withObjectScope("each", objSource);

        Object bare = resolver.resolve("${each.member}");
        assertThat(bare).isEqualTo("Alice");
    }

    @Test
    void drillOnly_fieldAccess_returnsTypedValue() {
        Map<String, Object> row = Map.of("name", "Alice", "port", 8080);
        ObjectVariableSource objSource = ObjectVariableSource.drillOnly(
                name -> "member".equals(name) ? row : null);
        VariableResolver resolver = new VariableResolver(Map.of(), Set.of())
                                            .withObjectScope("each", objSource);

        Object port = resolver.resolve("${each.member.port}");
        assertThat(port).isEqualTo(8080);
        assertThat(port).isInstanceOf(Integer.class);
    }

    @Test
    void defaultSource_bareMapReference_returnsMap() {
        Map<String, Object>  stepResult = Map.of("price", 42.5);
        ObjectVariableSource source     = name -> "step".equals(name) ? stepResult : null;
        VariableResolver resolver = new VariableResolver(Map.of(), Set.of())
                                            .withObjectScope("result", source);

        Object resolved = resolver.resolve("${result.step}");
        assertThat(resolved).isEqualTo(stepResult);
    }
}
