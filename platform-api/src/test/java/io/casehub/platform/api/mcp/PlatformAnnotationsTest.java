package io.casehub.platform.api.mcp;

import org.junit.jupiter.api.Test;
import java.lang.annotation.ElementType;
import java.lang.annotation.RetentionPolicy;
import static org.assertj.core.api.Assertions.assertThat;

class PlatformAnnotationsTest {

    @PlatformQuery("List items")
    void queryMethod() {}

    @PlatformMutation("Create item")
    void mutationMethod() {}

    @CallbackEligible(name = "test-spi")
    interface TestSpi {}

    @CallbackEligible
    interface DefaultNameSpi {}

    @CallbackEligible(fanOut = false)
    interface SingleImplSpi {}

    @Test
    void platformQuery_hasRuntimeRetention() throws Exception {
        var ann = getClass().getDeclaredMethod("queryMethod")
                .getAnnotation(PlatformQuery.class);
        assertThat(ann).isNotNull();
        assertThat(ann.value()).isEqualTo("List items");
        assertThat(PlatformQuery.class.getAnnotation(
                java.lang.annotation.Retention.class).value())
                .isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(PlatformQuery.class.getAnnotation(
                java.lang.annotation.Target.class).value())
                .containsExactly(ElementType.METHOD);
    }

    @Test
    void platformMutation_hasRuntimeRetention() throws Exception {
        var ann = getClass().getDeclaredMethod("mutationMethod")
                .getAnnotation(PlatformMutation.class);
        assertThat(ann).isNotNull();
        assertThat(ann.value()).isEqualTo("Create item");
        assertThat(PlatformMutation.class.getAnnotation(
                java.lang.annotation.Retention.class).value())
                .isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(PlatformMutation.class.getAnnotation(
                java.lang.annotation.Target.class).value())
                .containsExactly(ElementType.METHOD);
    }

    @Test
    void callbackEligible_explicitName() {
        var ann = TestSpi.class.getAnnotation(CallbackEligible.class);
        assertThat(ann).isNotNull();
        assertThat(ann.name()).isEqualTo("test-spi");
        assertThat(ann.fanOut()).isTrue();
    }

    @Test
    void callbackEligible_defaultNameIsEmpty() {
        var ann = DefaultNameSpi.class.getAnnotation(CallbackEligible.class);
        assertThat(ann.name()).isEmpty();
    }

    @Test
    void callbackEligible_singleImpl() {
        var ann = SingleImplSpi.class.getAnnotation(CallbackEligible.class);
        assertThat(ann.fanOut()).isFalse();
    }

    @Test
    void callbackEligible_hasTypeTarget() {
        assertThat(CallbackEligible.class.getAnnotation(
                java.lang.annotation.Target.class).value())
                .containsExactly(ElementType.TYPE);
        assertThat(CallbackEligible.class.getAnnotation(
                java.lang.annotation.Retention.class).value())
                .isEqualTo(RetentionPolicy.RUNTIME);
    }
}
