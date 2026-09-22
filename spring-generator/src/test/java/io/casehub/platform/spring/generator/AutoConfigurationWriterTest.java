package io.casehub.platform.spring.generator;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.WildcardTypeName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AutoConfigurationWriterTest {

    private final AutoConfigurationWriter writer = new AutoConfigurationWriter();

    private String autoConfigSource(List<JavaFile> files) {
        return files.get(0).toString();
    }

    // --- Legacy path tests (9-param compact constructor) ---

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

        String source = autoConfigSource(writer.generate("io.casehub.platform.view.spring",
                "ViewAutoConfiguration", descriptors));

        assertThat(source).contains("@AutoConfiguration");
        assertThat(source).contains("@ConditionalOnClass(SubjectViewEvaluator.class)");
        assertThat(source).contains("@Bean");
        assertThat(source).contains("SubjectViewEvaluator subjectViewEvaluator()");
        assertThat(source).contains("SubjectViewOrchestrator subjectViewOrchestrator(SubjectViewEvaluator evaluator,");
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

        String source = autoConfigSource(writer.generate("io.casehub.platform.spring",
                "PlatformDefaultsAutoConfiguration", descriptors));

        assertThat(source).contains("@ConditionalOnMissingBean");
        assertThat(source).contains("NoOpStore noOpStore()");
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

        String source = autoConfigSource(writer.generate("io.casehub.platform.spring",
                "OverrideAutoConfiguration", descriptors));

        assertThat(source).contains("@Primary");
        assertThat(source).contains("SomeService overrideService()");
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

        String source = autoConfigSource(writer.generate("io.casehub.engine.common.spring",
                "CommonAutoConfiguration", descriptors));

        assertThat(source).contains("SimpleBean simpleBean()");
        assertThat(source).doesNotContain("cdiBean");
        assertThat(source).doesNotContain("CdiBean");
    }

    @Test
    void generatesImportsFile() {
        String imports = writer.generateImportsFile(
                "io.casehub.platform.view.spring", "ViewAutoConfiguration");

        assertThat(imports).isEqualTo("io.casehub.platform.view.spring.ViewAutoConfiguration\n");
    }

    // --- Enhanced path tests (full constructor with constructorParams) ---

    @Test
    void enhancedPath_plainParams_emitsBeanWithConstructorCall() {
        var desc = enhancedDescriptor("myService", "io.casehub.MyService",
                List.of(new ProducerDescriptor.ConstructorParam(
                        "io.casehub.MyDep", "dep", ProducerDescriptor.ParamKind.PLAIN)),
                true, false);

        String source = autoConfigSource(writer.generate("io.casehub.spring",
                "TestAutoConfiguration", List.of(desc)));

        assertThat(source).contains("@Bean");
        assertThat(source).contains("@ConditionalOnMissingBean");
        assertThat(source).contains("MyService myService(MyDep dep)");
        assertThat(source).contains("return new MyService(dep);");
    }

    @Test
    void enhancedPath_listParam_emitsListParameter() {
        var desc = enhancedDescriptor("registry", "io.casehub.Registry",
                List.of(new ProducerDescriptor.ConstructorParam(
                        "io.casehub.Engine", "engines", ProducerDescriptor.ParamKind.LIST)),
                false, false);

        String source = autoConfigSource(writer.generate("io.casehub.spring",
                "TestAutoConfiguration", List.of(desc)));

        assertThat(source).contains("List<Engine> engines");
        assertThat(source).contains("return new Registry(engines);");
    }

    @Test
    void enhancedPath_optionalParam_emitsObjectProvider() {
        var desc = enhancedDescriptor("router", "io.casehub.Router",
                List.of(new ProducerDescriptor.ConstructorParam(
                        "io.casehub.Manifest", "manifest", ProducerDescriptor.ParamKind.OPTIONAL)),
                false, false);

        String source = autoConfigSource(writer.generate("io.casehub.spring",
                "TestAutoConfiguration", List.of(desc)));

        assertThat(source).contains("ObjectProvider<Manifest> manifest");
        assertThat(source).contains("return new Router(java.util.Optional.ofNullable(manifest.getIfAvailable()));");
    }

    @Test
    void enhancedPath_configProperties_emitsRecordAndEnableConfigProperties() {
        var configMethods = List.of(
                new ProducerDescriptor.ConfigPropertyMethod("name", "java.lang.String", "default-name"),
                new ProducerDescriptor.ConfigPropertyMethod("count", "int", null));
        var desc = new ProducerDescriptor(
                "io.casehub.quarkus.Beans", "myService",
                "io.casehub.MyService", null, List.of(),
                true, false, 0, false,
                List.of(new ProducerDescriptor.ConstructorParam(
                        "io.casehub.MyProperties", "config",
                        ProducerDescriptor.ParamKind.CONFIG_PROPERTIES)),
                "my.prefix", "io.casehub.MyProperties",
                List.of(), null, false, 0, false, null, configMethods);

        var files = writer.generate("io.casehub.spring", "TestAutoConfiguration", List.of(desc));
        assertThat(files).hasSize(2);

        String autoConfig = files.get(0).toString();
        assertThat(autoConfig).contains("@EnableConfigurationProperties(MyPropertiesSpring.class)");
        assertThat(autoConfig).contains("MyService myService(MyPropertiesSpring config)");

        String record = files.get(1).toString();
        assertThat(record).contains("@ConfigurationProperties(");
        assertThat(record).contains("prefix = \"my.prefix\"");
        assertThat(record).contains("record MyPropertiesSpring");
        assertThat(record).contains("implements MyProperties");
        assertThat(record).contains("String name");
        assertThat(record).contains("int count");
        assertThat(record).contains("@DefaultValue(\"default-name\")");
    }

    @Test
    void enhancedPath_factoryMethod_callsStaticFactory() {
        var desc = new ProducerDescriptor(
                "io.casehub.quarkus.Beans", "myService",
                "io.casehub.MyService", null, List.of(),
                false, false, 0, false,
                List.of(new ProducerDescriptor.ConstructorParam(
                        "io.casehub.MyDep", "dep", ProducerDescriptor.ParamKind.PLAIN)),
                null, null, List.of(), null, false, 0, true, "create", List.of());

        String source = autoConfigSource(writer.generate("io.casehub.spring",
                "TestAutoConfiguration", List.of(desc)));

        assertThat(source).contains("return MyService.create(dep);");
    }

    @Test
    void enhancedPath_primaryBean_emitsPrimary() {
        var desc = new ProducerDescriptor(
                "io.casehub.quarkus.Beans", "composite",
                "io.casehub.Composite", null, List.of(),
                false, false, 0, false,
                List.of(new ProducerDescriptor.ConstructorParam(
                        "io.casehub.Resolver", "resolvers",
                        ProducerDescriptor.ParamKind.LIST)),
                null, null,
                List.of("io.casehub.DIDMethod"),
                null, true, 0, false, null, List.of());

        String source = autoConfigSource(writer.generate("io.casehub.spring",
                "TestAutoConfiguration", List.of(desc)));

        assertThat(source).contains("@Primary");
        assertThat(source).contains("@DIDMethod");
    }

    @Test
    void enhancedPath_orderValue_emitsOrderAnnotation() {
        var desc = new ProducerDescriptor(
                "io.casehub.quarkus.Beans", "resolver",
                "io.casehub.KeyResolver", null, List.of(),
                false, false, 100, false,
                List.of(), null, null, List.of(), null, false, 100,
                false, null, List.of());

        String source = autoConfigSource(writer.generate("io.casehub.spring",
                "TestAutoConfiguration", List.of(desc)));

        assertThat(source).contains("@Order(100)");
    }

    @Test
    void enhancedPath_initMethod_emitsBeanInitMethod() {
        var desc = new ProducerDescriptor(
                "io.casehub.quarkus.Beans", "client",
                "io.casehub.Client", null, List.of(),
                false, false, 0, false,
                List.of(), null, null, List.of(), "validateBinary", false, 0,
                false, null, List.of());

        String source = autoConfigSource(writer.generate("io.casehub.spring",
                "TestAutoConfiguration", List.of(desc)));

        assertThat(source).contains("initMethod = \"validateBinary\"");
    }

    @Test
    void enhancedPath_anchorType_usesLexicographicallyEarliest() {
        var desc1 = enhancedDescriptor("zService", "io.casehub.ZService", List.of(), false, false);
        var desc2 = enhancedDescriptor("aService", "io.casehub.AService", List.of(), false, false);

        String source = autoConfigSource(writer.generate("io.casehub.spring",
                "TestAutoConfiguration", List.of(desc1, desc2)));

        assertThat(source).contains("@ConditionalOnClass(AService.class)");
    }


    @Test
    void enhancedPath_eventConsumerParam_emitsPublisherBridge() {
        var desc = enhancedDescriptor("summaryService", "io.casehub.SummaryService",
                                      List.of(
                                              new ProducerDescriptor.ConstructorParam(
                                                      "io.casehub.SummaryStore", "store",
                                                      ProducerDescriptor.ParamKind.PLAIN),
                                              new ProducerDescriptor.ConstructorParam(
                                                      "io.casehub.SummaryUpdatedEvent", "eventListener",
                                                      ProducerDescriptor.ParamKind.EVENT_CONSUMER)),
                                      false, false);

        String source = autoConfigSource(writer.generate("io.casehub.spring",
                                                         "TestAutoConfiguration", List.of(desc)));

        assertThat(source).contains("ApplicationEventPublisher publisher");
        assertThat(source).contains("SummaryStore store");
        assertThat(source).doesNotContain("Consumer");
        assertThat(source).contains("event -> publisher.publishEvent(event)");
        assertThat(source).contains("return new SummaryService(store, event -> publisher.publishEvent(event));");
    }
@Test
void enhancedPath_multipleEventConsumers_singlePublisher() {
    var desc = enhancedDescriptor("gateway", "io.casehub.Gateway",
            List.of(
                    new ProducerDescriptor.ConstructorParam(
                            "io.casehub.Dep", "dep",
                            ProducerDescriptor.ParamKind.PLAIN),
                    new ProducerDescriptor.ConstructorParam(
                            "io.casehub.InitEvent", "initListener",
                            ProducerDescriptor.ParamKind.EVENT_CONSUMER),
                    new ProducerDescriptor.ConstructorParam(
                            "io.casehub.CloseEvent", "closeListener",
                            ProducerDescriptor.ParamKind.EVENT_CONSUMER)),
            false, false);

    String source = autoConfigSource(writer.generate("io.casehub.spring",
            "TestAutoConfiguration", List.of(desc)));

    assertThat(source).containsOnlyOnce("ApplicationEventPublisher publisher");
    assertThat(source).contains("return new Gateway(dep, event -> publisher.publishEvent(event), event -> publisher.publishEvent(event));");
}
@Test
void enhancedPath_supplierDepParam_emitsObjectProviderSupplier() {
    var desc = enhancedDescriptor("gateway", "io.casehub.Gateway",
            List.of(
                    new ProducerDescriptor.ConstructorParam(
                            "io.casehub.Dep", "dep",
                            ProducerDescriptor.ParamKind.PLAIN),
                    new ProducerDescriptor.ConstructorParam(
                            "io.casehub.Tracer", "tracer",
                            ProducerDescriptor.ParamKind.SUPPLIER_DEP)),
            false, false);

    String source = autoConfigSource(writer.generate("io.casehub.spring",
            "TestAutoConfiguration", List.of(desc)));

    assertThat(source).contains("ObjectProvider<Tracer> tracer");
    assertThat(source).doesNotContain("Supplier");
    assertThat(source).contains("tracer.getIfAvailable() != null ? tracer::getObject : null");
}


    @Test
    void enhancedPath_wildcardListParam_preservesGenericWildcard() {
        ParameterizedTypeName wildcardType = ParameterizedTypeName.get(
                ClassName.get("io.casehub", "GenericInterface"),
                WildcardTypeName.subtypeOf(Object.class));
        var desc = enhancedDescriptor("registry", "io.casehub.Registry",
                                      List.of(new ProducerDescriptor.ConstructorParam(
                                              "io.casehub.GenericInterface", "items",
                                              ProducerDescriptor.ParamKind.LIST, wildcardType)),
                                      false, false);

        String source = autoConfigSource(writer.generate("io.casehub.spring",
                                                         "TestAutoConfiguration", List.of(desc)));

        assertThat(source).contains("List<GenericInterface<?>> items");
        assertThat(source).contains("return new Registry(items);");
    }

    private ProducerDescriptor enhancedDescriptor(String methodName, String returnType,
                                                   List<ProducerDescriptor.ConstructorParam> params,
                                                   boolean defaultBean, boolean alternative) {
        return new ProducerDescriptor(
                "io.casehub.quarkus.Beans", methodName, returnType, null, List.of(),
                defaultBean, alternative, 0, false,
                params, null, null, List.of(), null, false, 0, false, null, List.of());
    }
}
