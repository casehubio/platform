package io.casehub.platform.graphql.generator.infer;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VarResolutionTest {

    private static CompileTestHelper helper;

    @BeforeAll
    static void compile() throws Exception {
        helper = new CompileTestHelper("StandardConnector.java", "VarConnector.java", "ContactsConnector.java");
    }

    @Test
    void var_with_return_infers_full_chain() {
        InferredCallChain result = MethodBodyInferrer.infer(
                helper.trees, helper.findMethod("VarConnector", "varWithReturn"), null);
        System.out.println("spiTypeFqcn=" + result.spiTypeFqcn());
        System.out.println("routingParam=" + result.routingParamName());
        System.out.println("capability=" + result.capabilityFqcn());
        System.out.println("accessor=" + result.capabilityAccessor());
        System.out.println("targetMethod=" + result.targetMethod());
        System.out.println("failureReason=" + result.failureReason());
        System.out.println("isComplete=" + result.isComplete());
        assertThat(result.isComplete()).isTrue();
        assertThat(result.spiTypeFqcn()).isNotNull();
        assertThat(result.targetMethod()).isEqualTo("targetMethod");
    }

    @Test
    void var_no_capability_routing() {
        InferredCallChain result = MethodBodyInferrer.infer(
                helper.trees, helper.findMethod("VarConnector", "varNoCapabilityRouting"), null);
        System.out.println("spiTypeFqcn=" + result.spiTypeFqcn());
        System.out.println("targetMethod=" + result.targetMethod());
        System.out.println("failureReason=" + result.failureReason());
        assertThat(result.isComplete()).isTrue();
        assertThat(result.targetMethod()).isEqualTo("listAll");
    }

    @Test
    void var_void_method() {
        InferredCallChain result = MethodBodyInferrer.infer(
                helper.trees, helper.findMethod("VarConnector", "varVoidMethod"), null);
        System.out.println("spiTypeFqcn=" + result.spiTypeFqcn());
        System.out.println("targetMethod=" + result.targetMethod());
        System.out.println("failureReason=" + result.failureReason());
        assertThat(result.isComplete()).isTrue();
        assertThat(result.targetMethod()).isEqualTo("deleteItem");
    }

    // -- Contacts-style reproduction (issue #802) --

    @Test
    void contacts_listContacts_with_var() {
        InferredCallChain result = MethodBodyInferrer.infer(
                helper.trees, helper.findMethod("ContactsConnector", "listContacts"), null);
        System.out.println("=== ContactsConnector.listContacts ===");
        System.out.println("spiTypeFqcn=" + result.spiTypeFqcn());
        System.out.println("routingParam=" + result.routingParamName());
        System.out.println("capability=" + result.capabilityFqcn());
        System.out.println("accessor=" + result.capabilityAccessor());
        System.out.println("targetMethod=" + result.targetMethod());
        System.out.println("targetArgs=" + result.targetArgs());
        System.out.println("staticDefaults=" + result.staticDefaults());
        System.out.println("failureReason=" + result.failureReason());
        System.out.println("isComplete=" + result.isComplete());
        assertThat(result.isComplete()).isTrue();
        assertThat(result.targetMethod()).isEqualTo("list");
        assertThat(result.capabilityAccessor()).isEqualTo("contactRead");
    }

    @Test
    void contacts_getContact_with_var() {
        InferredCallChain result = MethodBodyInferrer.infer(
                helper.trees, helper.findMethod("ContactsConnector", "getContact"), null);
        System.out.println("=== ContactsConnector.getContact ===");
        System.out.println("spiTypeFqcn=" + result.spiTypeFqcn());
        System.out.println("targetMethod=" + result.targetMethod());
        System.out.println("failureReason=" + result.failureReason());
        assertThat(result.isComplete()).isTrue();
        assertThat(result.targetMethod()).isEqualTo("get");
    }
}
