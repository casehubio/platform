package io.casehub.platform.spring.generator;

import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JandexProducerScannerTest {

    @Test
    void findsProducesMethodsWithReturnTypesAndParameters() throws IOException {
        Index index = indexClasses(SampleBeans.class, SampleService.class, SampleOrchestrator.class, SampleStore.class);
        var scanner = new JandexProducerScanner();

        List<ProducerDescriptor> descriptors = scanner.scan(index);

        assertThat(descriptors).hasSize(2);

        var simple = descriptors.stream()
                .filter(d -> d.methodName().equals("simpleBean"))
                .findFirst().orElseThrow();
        assertThat(simple.returnType()).endsWith("SampleService");
        assertThat(simple.parameters()).isEmpty();
        assertThat(simple.defaultBean()).isFalse();

        var withDeps = descriptors.stream()
                .filter(d -> d.methodName().equals("beanWithDeps"))
                .findFirst().orElseThrow();
        assertThat(withDeps.returnType()).endsWith("SampleOrchestrator");
        assertThat(withDeps.parameters()).hasSize(2);
        assertThat(withDeps.parameters().get(0).type()).endsWith("SampleService");
    }

    @Test
    void detectsDefaultBeanAnnotation() throws IOException {
        Index index = indexClasses(SampleDefaultBeans.class, SampleNoOp.class);
        var scanner = new JandexProducerScanner();

        List<ProducerDescriptor> descriptors = scanner.scan(index);

        assertThat(descriptors).hasSize(1);
        assertThat(descriptors.get(0).defaultBean()).isTrue();
        assertThat(descriptors.get(0).returnType()).endsWith("SampleNoOp");
    }

    @Test
    void detectsAlternativeWithPriority() throws IOException {
        Index index = indexClasses(SampleAlternativeBeans.class, SampleService.class);
        var scanner = new JandexProducerScanner();

        List<ProducerDescriptor> descriptors = scanner.scan(index);

        assertThat(descriptors).hasSize(1);
        assertThat(descriptors.get(0).alternative()).isTrue();
        assertThat(descriptors.get(0).priority()).isEqualTo(100);
    }

    // --- Constructor-following tests ---

    @Test
    void followsReturnType_simplePojo_noArgs() throws IOException {
        Index scanIndex = indexClasses(SampleConstructorBeans.class, SimplePojo.class, SampleConfig.class,
                SampleProperties.class, SomeInterface.class, SomeDep.class, ConfigPojo.class,
                ListPojo.class, OptionalPojo.class, FactoryPojo.class);
        var scanner = new JandexProducerScanner();

        List<ProducerDescriptor> descriptors = scanner.scan(scanIndex);

        var desc = findByMethod(descriptors, "simplePojo");
        assertThat(desc.constructorResolved()).isTrue();
        assertThat(desc.constructorParams()).isEmpty();
        assertThat(desc.requiresManualConfig()).isFalse();
    }

    @Test
    void followsReturnType_configProperties() throws IOException {
        Index scanIndex = indexClasses(SampleConstructorBeans.class, SimplePojo.class, SampleConfig.class,
                SampleProperties.class, SomeInterface.class, SomeDep.class, ConfigPojo.class,
                ListPojo.class, OptionalPojo.class, FactoryPojo.class);
        var scanner = new JandexProducerScanner();

        List<ProducerDescriptor> descriptors = scanner.scan(scanIndex);

        var desc = findByMethod(descriptors, "configPojo");
        assertThat(desc.constructorResolved()).isTrue();
        assertThat(desc.constructorParams()).hasSize(1);
        assertThat(desc.constructorParams().get(0).kind()).isEqualTo(ProducerDescriptor.ParamKind.CONFIG_PROPERTIES);
        assertThat(desc.configPropertiesPrefix()).isEqualTo("sample.config");
        assertThat(desc.requiresManualConfig()).isFalse();
    }

    @Test
    void followsReturnType_listParam() throws IOException {
        Index scanIndex = indexClasses(SampleConstructorBeans.class, SimplePojo.class, SampleConfig.class,
                SampleProperties.class, SomeInterface.class, SomeDep.class, ConfigPojo.class,
                ListPojo.class, OptionalPojo.class, FactoryPojo.class);
        var scanner = new JandexProducerScanner();

        List<ProducerDescriptor> descriptors = scanner.scan(scanIndex);

        var desc = findByMethod(descriptors, "listPojo");
        assertThat(desc.constructorResolved()).isTrue();
        assertThat(desc.constructorParams()).hasSize(1);
        assertThat(desc.constructorParams().get(0).kind()).isEqualTo(ProducerDescriptor.ParamKind.LIST);
        assertThat(desc.constructorParams().get(0).type()).endsWith("SomeInterface");
        assertThat(desc.requiresManualConfig()).isFalse();
    }

    @Test
    void followsReturnType_optionalParam() throws IOException {
        Index scanIndex = indexClasses(SampleConstructorBeans.class, SimplePojo.class, SampleConfig.class,
                SampleProperties.class, SomeInterface.class, SomeDep.class, ConfigPojo.class,
                ListPojo.class, OptionalPojo.class, FactoryPojo.class);
        var scanner = new JandexProducerScanner();

        List<ProducerDescriptor> descriptors = scanner.scan(scanIndex);

        var desc = findByMethod(descriptors, "optionalPojo");
        assertThat(desc.constructorResolved()).isTrue();
        assertThat(desc.constructorParams()).hasSize(1);
        assertThat(desc.constructorParams().get(0).kind()).isEqualTo(ProducerDescriptor.ParamKind.OPTIONAL);
        assertThat(desc.constructorParams().get(0).type()).endsWith("SomeDep");
        assertThat(desc.requiresManualConfig()).isFalse();
    }

    @Test
    void detectsFactoryMethod() throws IOException {
        Index scanIndex = indexClasses(SampleConstructorBeans.class, SimplePojo.class, SampleConfig.class,
                SampleProperties.class, SomeInterface.class, SomeDep.class, ConfigPojo.class,
                ListPojo.class, OptionalPojo.class, FactoryPojo.class,
                io.casehub.platform.api.FactoryMethod.class);
        var scanner = new JandexProducerScanner();

        List<ProducerDescriptor> descriptors = scanner.scan(scanIndex);

        var desc = findByMethod(descriptors, "factoryPojo");
        assertThat(desc.hasFactoryMethod()).isTrue();
        assertThat(desc.factoryMethodName()).isEqualTo("create");
        assertThat(desc.constructorParams()).hasSize(2);
        assertThat(desc.constructorParams().get(0).kind()).isEqualTo(ProducerDescriptor.ParamKind.LIST);
        assertThat(desc.constructorParams().get(1).kind()).isEqualTo(ProducerDescriptor.ParamKind.OPTIONAL);
        assertThat(desc.requiresManualConfig()).isFalse();
    }

    @Test
    void detectsPostConstructInitMethod() throws IOException {
        Index scanIndex = indexClasses(SampleInitBeans.class, SimplePojo.class);
        var scanner = new JandexProducerScanner();

        List<ProducerDescriptor> descriptors = scanner.scan(scanIndex);

        var desc = findByMethod(descriptors, "initPojo");
        assertThat(desc.initMethod()).isEqualTo("validate");
    }

    @Test
    void detectsPriorityAsOrderValue() throws IOException {
        Index scanIndex = indexClasses(SamplePriorityBeans.class, SimplePojo.class);
        var scanner = new JandexProducerScanner();

        List<ProducerDescriptor> descriptors = scanner.scan(scanIndex);

        var desc = findByMethod(descriptors, "priorityPojo");
        assertThat(desc.orderValue()).isEqualTo(42);
    }

    @Test
    void detectsQualifiersOnInstanceParam() throws IOException {
        Index scanIndex = indexClasses(SampleQualifierBeans.class, SomeInterface.class, ListPojo.class,
                SampleQualifier.class);
        var scanner = new JandexProducerScanner();

        List<ProducerDescriptor> descriptors = scanner.scan(scanIndex);

        var desc = findByMethod(descriptors, "compositePojo");
        assertThat(desc.hasQualifiers()).isTrue();
        assertThat(desc.qualifiers()).contains("io.casehub.platform.spring.generator.SampleQualifier");
        assertThat(desc.primaryBean()).isTrue();
    }

    // --- Helpers ---


    @Test
    void followsReturnType_consumerParam_detectsEventConsumer() throws IOException {
        Index scanIndex = indexClasses(SampleConstructorBeans.class, SimplePojo.class,
                                       SampleConfig.class, SampleProperties.class, SomeInterface.class,
                                       SomeDep.class, ConfigPojo.class, ListPojo.class, OptionalPojo.class,
                                       FactoryPojo.class, EventConsumerPojo.class);
        var scanner = new JandexProducerScanner();

        List<ProducerDescriptor> descriptors = scanner.scan(scanIndex);

        var desc = findByMethod(descriptors, "eventConsumerPojo");
        assertThat(desc.constructorResolved()).isTrue();
        assertThat(desc.constructorParams()).hasSize(2);
        assertThat(desc.constructorParams().get(0).kind())
                .isEqualTo(ProducerDescriptor.ParamKind.PLAIN);
        assertThat(desc.constructorParams().get(1).kind())
                .isEqualTo(ProducerDescriptor.ParamKind.EVENT_CONSUMER);
        assertThat(desc.requiresManualConfig()).isFalse();
    }

    private ProducerDescriptor findByMethod(List<ProducerDescriptor> descriptors, String methodName) {
        return descriptors.stream()
                .filter(d -> d.methodName().equals(methodName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No descriptor for method: " + methodName
                        + ". Available: " + descriptors.stream().map(ProducerDescriptor::methodName).toList()));
    }

    private Index indexClasses(Class<?>... classes) throws IOException {
        Indexer indexer = new Indexer();
        for (Class<?> clazz : classes) {
            String resourceName = clazz.getName().replace('.', '/') + ".class";
            try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourceName)) {
                if (is != null) {
                    indexer.index(is);
                }
            }
        }
        return indexer.complete();
    }
}
