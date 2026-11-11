package io.casehub.platform.graphql.generator.infer;

import com.sun.source.util.JavacTask;
import com.sun.source.util.Trees;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CallChainScannerTest {

    private static Trees trees;
    private static TypeElement connectorType;

    @BeforeAll
    static void compileTestSource() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        StandardJavaFileManager fm = compiler.getStandardFileManager(null, null, null);
        File source = new File("src/test/resources/infer/StandardConnector.java");
        Iterable<? extends JavaFileObject> units = fm.getJavaFileObjects(source);
        JavacTask task = (JavacTask) compiler.getTask(null, fm, null,
                List.of("-proc:none"), null, units);
        Iterable<? extends Element> elements = task.analyze();
        trees = Trees.instance(task);
        for (Element e : elements) {
            if (e instanceof TypeElement te
                    && te.getSimpleName().contentEquals("StandardConnector")) {
                connectorType = te;
                break;
            }
        }
        assertThat(connectorType).as("StandardConnector type must be found").isNotNull();
    }

    private ExecutableElement findMethod(String name) {
        for (Element e : connectorType.getEnclosedElements()) {
            if (e instanceof ExecutableElement exec
                    && exec.getSimpleName().contentEquals(name)) {
                return exec;
            }
        }
        throw new AssertionError("Method not found: " + name);
    }

    @Test
    void pattern_a_simple_query_infers_call_chain() {
        InferredCallChain result = MethodBodyInferrer.infer(trees, findMethod("simpleQuery"), null);
        assertThat(result.isComplete()).isTrue();
        assertThat(result.spiTypeFqcn()).isEqualTo("FakeSpiType");
        assertThat(result.routingParamName()).isEqualTo("platform");
        assertThat(result.capabilityAccessor()).isEqualTo("capability");
        assertThat(result.targetMethod()).isEqualTo("targetMethod");
        assertThat(result.targetArgs()).containsExactly("accountId");
    }

    @Test
    void require_capability_extracts_class_and_method() {
        InferredCallChain result = MethodBodyInferrer.infer(trees, findMethod("withRequireCapability"), null);
        assertThat(result.isComplete()).isTrue();
        assertThat(result.capabilityFqcn()).isEqualTo("FakeCapability");
        assertThat(result.requireCapabilityMethodName()).isEqualTo("targetMethod");
    }

    @Test
    void no_capability_routing_infers_direct_call() {
        InferredCallChain result = MethodBodyInferrer.infer(trees, findMethod("noCapabilityRouting"), null);
        assertThat(result.isComplete()).isTrue();
        assertThat(result.capabilityAccessor()).isNull();
        assertThat(result.capabilityFqcn()).isNull();
        assertThat(result.targetMethod()).isEqualTo("listAll");
    }

    @Test
    void multiple_accessor_args_captured() {
        InferredCallChain result = MethodBodyInferrer.infer(trees, findMethod("multipleAccessorArgs"), null);
        assertThat(result.isComplete()).isTrue();
        assertThat(result.accessorArgs()).hasSize(2);
        assertThat(result.accessorArgs()).containsExactly("userId", "\"extra\"");
    }

    @Test
    void pattern_b_helper_method_routing() {
        InferredCallChain result = MethodBodyInferrer.infer(trees, findMethod("helperMethodRouting"), null);
        assertThat(result.isComplete()).isTrue();
        assertThat(result.spiTypeFqcn()).isEqualTo("FakeSpiType");
        assertThat(result.capabilityFqcn()).isEqualTo("FakeCapability");
        assertThat(result.targetMethod()).isEqualTo("targetMethod");
    }
}
