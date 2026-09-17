package io.casehub.platform.spring.generator;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AutoConfigurationWriterTest {

    private final AutoConfigurationWriter writer = new AutoConfigurationWriter();

    @Test
    void generatesAutoConfigurationWithBeanMethods() {
        var descriptors = List.of(
                new ProducerDescriptor(
                        "io.casehub.platform.view.quarkus.ViewBeans",
                        "subjectViewEvaluator",
                        "io.casehub.platform.view.SubjectViewEvaluator",
                        null,
                        List.of(),
                        false, false, 0, false),
                new ProducerDescriptor(
                        "io.casehub.platform.view.quarkus.ViewBeans",
                        "subjectViewOrchestrator",
                        "io.casehub.platform.view.SubjectViewOrchestrator",
                        null,
                        List.of(
                                new ProducerDescriptor.ParameterDescriptor(
                                        "io.casehub.platform.view.SubjectViewEvaluator",
                                        "evaluator", null),
                                new ProducerDescriptor.ParameterDescriptor(
                                        "io.casehub.platform.api.view.SubjectViewStore",
                                        "viewStore", null)
                        ),
                        false, false, 0, false)
        );

        String source = writer.generate("io.casehub.platform.view.spring",
                "ViewAutoConfiguration", descriptors);

        assertThat(source).contains("@AutoConfiguration");
        assertThat(source).contains("@ConditionalOnClass(SubjectViewEvaluator.class)");
        assertThat(source).contains("@Bean");
        assertThat(source).contains("public SubjectViewEvaluator subjectViewEvaluator()");
        assertThat(source).contains("public SubjectViewOrchestrator subjectViewOrchestrator(SubjectViewEvaluator evaluator, SubjectViewStore viewStore)");
        assertThat(source).contains("return new SubjectViewOrchestrator(evaluator, viewStore);");
    }

    @Test
    void addsConditionalOnMissingBeanForDefaultBeans() {
        var descriptors = List.of(
                new ProducerDescriptor(
                        "io.casehub.platform.quarkus.DefaultBeans",
                        "noOpStore",
                        "io.casehub.platform.mock.NoOpStore",
                        null,
                        List.of(),
                        true, false, 0, false)
        );

        String source = writer.generate("io.casehub.platform.spring",
                "PlatformDefaultsAutoConfiguration", descriptors);

        assertThat(source).contains("@ConditionalOnMissingBean");
        assertThat(source).contains("public NoOpStore noOpStore()");
    }

    @Test
    void addsPrimaryForAlternativeBeans() {
        var descriptors = List.of(
                new ProducerDescriptor(
                        "io.casehub.platform.quarkus.OverrideBeans",
                        "overrideService",
                        "io.casehub.platform.SomeService",
                        null,
                        List.of(),
                        false, true, 100, false)
        );

        String source = writer.generate("io.casehub.platform.spring",
                "OverrideAutoConfiguration", descriptors);

        assertThat(source).contains("@Primary");
        assertThat(source).contains("public SomeService overrideService()");
    }

    @Test
    void skipsCdiDependentMethods() {
        var descriptors = List.of(
                new ProducerDescriptor(
                        "io.casehub.engine.common.quarkus.CommonBeans",
                        "simpleBean",
                        "io.casehub.engine.common.SimpleBean",
                        null,
                        List.of(),
                        false, false, 0, false),
                new ProducerDescriptor(
                        "io.casehub.engine.common.quarkus.CommonBeans",
                        "cdiBean",
                        "io.casehub.engine.common.CdiBean",
                        null,
                        List.of(
                                new ProducerDescriptor.ParameterDescriptor(
                                        "jakarta.enterprise.inject.Instance",
                                        "resolvers", null)
                               ),
                        false, false, 0, true)
                                 );

        String source = writer.generate("io.casehub.engine.common.spring",
                                        "CommonAutoConfiguration", descriptors);

        assertThat(source).contains("public SimpleBean simpleBean()");
        assertThat(source).doesNotContain("cdiBean");
        assertThat(source).doesNotContain("CdiBean");
    }


    @Test
    void generatesImportsFile() {
        String imports = writer.generateImportsFile(
                "io.casehub.platform.view.spring", "ViewAutoConfiguration");

        assertThat(imports).isEqualTo("io.casehub.platform.view.spring.ViewAutoConfiguration\n");
    }
}
