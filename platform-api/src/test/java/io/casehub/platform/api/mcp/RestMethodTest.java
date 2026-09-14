package io.casehub.platform.api.mcp;

import org.junit.jupiter.api.Test;
import java.lang.annotation.ElementType;
import java.lang.annotation.RetentionPolicy;
import static org.assertj.core.api.Assertions.assertThat;

class RestMethodTest {

    @Test
    void restMethod_hasRuntimeRetention() {
        var retention = RestMethod.class.getAnnotation(java.lang.annotation.Retention.class);
        assertThat(retention.value()).isEqualTo(RetentionPolicy.RUNTIME);
    }

    @Test
    void restMethod_targetsMethod() {
        var target = RestMethod.class.getAnnotation(java.lang.annotation.Target.class);
        assertThat(target.value()).containsExactly(ElementType.METHOD);
    }

    @Test
    void pathParam_hasRuntimeRetention() {
        var retention = PathParam.class.getAnnotation(java.lang.annotation.Retention.class);
        assertThat(retention.value()).isEqualTo(RetentionPolicy.RUNTIME);
    }

    @Test
    void pathParam_targetsParameter() {
        var target = PathParam.class.getAnnotation(java.lang.annotation.Target.class);
        assertThat(target.value()).containsExactly(ElementType.PARAMETER);
    }

    @Test
    void httpMethod_hasAllVerbs() {
        assertThat(HttpMethod.values()).containsExactly(
            HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT,
            HttpMethod.DELETE, HttpMethod.PATCH);
    }
}
