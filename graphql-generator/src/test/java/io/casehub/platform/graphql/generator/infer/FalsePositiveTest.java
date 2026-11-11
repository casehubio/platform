package io.casehub.platform.graphql.generator.infer;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FalsePositiveTest {

    private static CompileTestHelper helper;

    @BeforeAll
    static void compile() throws Exception {
        helper = new CompileTestHelper("StandardConnector.java", "FalsePositiveConnector.java");
    }

    @Test
    void var_typed_spi_infers_successfully() {
        // On JDK 26, VariableTree.getType() resolves var to the inferred type,
        // so var-typed SPI variables work — the scanner sees the resolved type name
        InferredCallChain result = MethodBodyInferrer.infer(
                helper.trees, helper.findMethod("FalsePositiveConnector", "varTypedSpi"), null);
        assertThat(result.isComplete()).isTrue();
        assertThat(result.spiTypeFqcn()).isNotNull();
        assertThat(result.targetMethod()).isEqualTo("targetMethod");
    }

    @Test
    void multiple_returns_fails_inference() {
        InferredCallChain result = MethodBodyInferrer.infer(
                helper.trees, helper.findMethod("FalsePositiveConnector", "multipleReturns"), null);
        assertThat(result.isComplete()).isFalse();
        assertThat(result.failureReason()).isEqualTo(InferenceFailure.MULTIPLE_RETURNS);
    }

    @Test
    void lambda_return_does_not_trigger_multiple_returns() {
        InferredCallChain result = MethodBodyInferrer.infer(
                helper.trees, helper.findMethod("FalsePositiveConnector", "lambdaReturn"), null);
        assertThat(result.isComplete()).isTrue();
        assertThat(result.failureReason()).isNull();
    }

    @Test
    void no_spi_variable_fails_inference() {
        InferredCallChain result = MethodBodyInferrer.infer(
                helper.trees, helper.findMethod("FalsePositiveConnector", "noSpiVariable"), null);
        assertThat(result.isComplete()).isFalse();
        assertThat(result.failureReason()).isEqualTo(InferenceFailure.NO_SPI_VARIABLE);
    }
}
