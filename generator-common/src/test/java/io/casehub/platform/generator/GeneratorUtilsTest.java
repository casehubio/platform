package io.casehub.platform.generator;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GeneratorUtilsTest {

    @Test
    void deriveTargetPackage_stripsSourceSuffixAndAppendsTarget() {
        assertThat(GeneratorUtils.deriveTargetPackage(
                "io.casehub.platform.agent.claude.quarkus.ClaudeBeans",
                "quarkus", "spring"))
                .isEqualTo("io.casehub.platform.agent.claude.spring");
    }

    @Test
    void deriveTargetPackage_noSourceSuffix_appendsTargetDirectly() {
        assertThat(GeneratorUtils.deriveTargetPackage(
                "io.casehub.platform.mcp.CaseHubMcpTools",
                "", "spring"))
                .isEqualTo("io.casehub.platform.mcp.spring");
    }

    @Test
    void deriveTargetPackage_nullSourceSuffix_appendsTargetDirectly() {
        assertThat(GeneratorUtils.deriveTargetPackage(
                "io.casehub.platform.mcp.CaseHubMcpTools",
                null, "spring"))
                .isEqualTo("io.casehub.platform.mcp.spring");
    }

    @Test
    void deriveTargetPackage_sourceSuffixNotPresent_appendsTargetDirectly() {
        assertThat(GeneratorUtils.deriveTargetPackage(
                "io.casehub.platform.agent.claude.ClaudeBeans",
                "quarkus", "spring"))
                .isEqualTo("io.casehub.platform.agent.claude.spring");
    }

    @Test
    void deriveTargetPackage_noDots_returnsClassNameWithTarget() {
        assertThat(GeneratorUtils.deriveTargetPackage("MyClass", "", "spring"))
                .isEqualTo("MyClass.spring");
    }

    @Test
    void deriveTargetPackage_customSuffixes() {
        assertThat(GeneratorUtils.deriveTargetPackage(
                "com.example.cdi.MyBeans",
                "cdi", "springboot"))
                .isEqualTo("com.example.springboot");
    }
}
