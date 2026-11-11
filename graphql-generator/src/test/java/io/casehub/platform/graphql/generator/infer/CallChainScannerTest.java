package io.casehub.platform.graphql.generator.infer;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CallChainScannerTest {

    private static CompileTestHelper helper;

    @BeforeAll
    static void compile() throws Exception {
        helper = new CompileTestHelper(
                "StandardConnector.java",
                "DefaultValueConnector.java",
                "VoidConnector.java");
    }

    // -- Pattern A: standard routing --

    @Test
    void pattern_a_simple_query_infers_call_chain() {
        InferredCallChain result = MethodBodyInferrer.infer(
                helper.trees, helper.findMethod("StandardConnector", "simpleQuery"), null);
        assertThat(result.isComplete()).isTrue();
        assertThat(result.spiTypeFqcn()).isEqualTo("infer.FakeSpiType");
        assertThat(result.routingParamName()).isEqualTo("platform");
        assertThat(result.capabilityAccessor()).isEqualTo("capability");
        assertThat(result.targetMethod()).isEqualTo("targetMethod");
        assertThat(result.targetArgs()).containsExactly("accountId");
    }

    @Test
    void require_capability_extracts_class_and_method() {
        InferredCallChain result = MethodBodyInferrer.infer(
                helper.trees, helper.findMethod("StandardConnector", "withRequireCapability"), null);
        assertThat(result.isComplete()).isTrue();
        assertThat(result.capabilityFqcn()).isEqualTo("infer.FakeCapability");
        assertThat(result.requireCapabilityMethodName()).isEqualTo("targetMethod");
    }

    @Test
    void no_capability_routing_infers_direct_call() {
        InferredCallChain result = MethodBodyInferrer.infer(
                helper.trees, helper.findMethod("StandardConnector", "noCapabilityRouting"), null);
        assertThat(result.isComplete()).isTrue();
        assertThat(result.capabilityAccessor()).isNull();
        assertThat(result.capabilityFqcn()).isNull();
        assertThat(result.targetMethod()).isEqualTo("listAll");
    }

    @Test
    void multiple_accessor_args_captured() {
        InferredCallChain result = MethodBodyInferrer.infer(
                helper.trees, helper.findMethod("StandardConnector", "multipleAccessorArgs"), null);
        assertThat(result.isComplete()).isTrue();
        assertThat(result.accessorArgs()).hasSize(2);
    }

    // -- Pattern B: helper-method routing --

    @Test
    void pattern_b_helper_method_routing() {
        InferredCallChain result = MethodBodyInferrer.infer(
                helper.trees, helper.findMethod("StandardConnector", "helperMethodRouting"), null);
        assertThat(result.isComplete()).isTrue();
        assertThat(result.spiTypeFqcn()).isEqualTo("infer.FakeSpiType");
        assertThat(result.capabilityFqcn()).isEqualTo("infer.FakeCapability");
        assertThat(result.targetMethod()).isEqualTo("targetMethod");
    }

    // -- Rule 5: static default detection --

    @Test
    void static_default_integer_detected() {
        InferredCallChain result = MethodBodyInferrer.infer(
                helper.trees, helper.findMethod("DefaultValueConnector", "withStaticIntDefault"), null);
        assertThat(result.isComplete()).isTrue();
        assertThat(result.staticDefaults()).containsEntry("pageSize", "50");
    }

    @Test
    void static_default_string_detected() {
        InferredCallChain result = MethodBodyInferrer.infer(
                helper.trees, helper.findMethod("DefaultValueConnector", "withStaticStringDefault"), null);
        assertThat(result.isComplete()).isTrue();
        assertThat(result.staticDefaults()).containsEntry("format", "json");
    }

    // -- Rule 6: void method expression statement --

    @Test
    void void_method_target_extracted_via_rule_6() {
        InferredCallChain result = MethodBodyInferrer.infer(
                helper.trees, helper.findMethod("VoidConnector", "deleteItem"), null);
        assertThat(result.isComplete()).isTrue();
        assertThat(result.targetMethod()).isEqualTo("deleteItem");
        assertThat(result.capabilityAccessor()).isEqualTo("capability");
    }

    @Test
    void void_method_direct_call_via_rule_6() {
        InferredCallChain result = MethodBodyInferrer.infer(
                helper.trees, helper.findMethod("VoidConnector", "directVoidCall"), null);
        assertThat(result.isComplete()).isTrue();
        assertThat(result.targetMethod()).isEqualTo("removeItem");
        assertThat(result.capabilityAccessor()).isNull();
    }


    @org.junit.jupiter.api.Nested
    class StarImportResolution {

        private CompileTestHelper starHelper;

        @org.junit.jupiter.api.BeforeEach
        void compile() throws Exception {
            starHelper = new CompileTestHelper(
                    "StarImportConnector.java",
                    "spi/StarSpiType.java",
                    "spi/StarCapability.java",
                    "spi/StarResult.java",
                    "spi/StarPlatformService.java");
        }

        @Test
        void star_import_resolves_spi_type() {
            InferredCallChain result = MethodBodyInferrer.infer(
                    starHelper.trees, starHelper.findMethod("StarImportConnector", "simpleQuery"), null);
            assertThat(result.isComplete()).isTrue();
            assertThat(result.spiTypeFqcn()).isEqualTo("infer.spi.StarSpiType");
            assertThat(result.targetMethod()).isEqualTo("targetMethod");
        }

        @Test
        void star_import_direct_call() {
            InferredCallChain result = MethodBodyInferrer.infer(
                    starHelper.trees, starHelper.findMethod("StarImportConnector", "noCapabilityRouting"), null);
            assertThat(result.isComplete()).isTrue();
            assertThat(result.spiTypeFqcn()).isEqualTo("infer.spi.StarSpiType");
            assertThat(result.targetMethod()).isEqualTo("listAll");
        }
    }

}
