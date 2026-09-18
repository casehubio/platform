package io.casehub.platform.simulation;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RestInvocationTest {

    @Test
    void recordFieldsAreAccessible() {
        var invocation = new RestInvocation(
                "scim", "membersOf", "GET", "/Groups/{id}/Members",
                Map.of("id", "grp-1"), null);

        assertThat(invocation.spiName()).isEqualTo("scim");
        assertThat(invocation.methodName()).isEqualTo("membersOf");
        assertThat(invocation.httpMethod()).isEqualTo("GET");
        assertThat(invocation.pathTemplate()).isEqualTo("/Groups/{id}/Members");
        assertThat(invocation.params()).containsEntry("id", "grp-1");
        assertThat(invocation.body()).isNull();
    }

    @Test
    void bodyIsPreserved() {
        var body = Map.of("displayName", "Test Group");
        var invocation = new RestInvocation(
                "scim", "createGroup", "POST", "/Groups",
                Map.of(), body);

        assertThat(invocation.body()).isEqualTo(body);
    }

    @Test
    void nullHttpMethodIsAllowed() {
        var invocation = new RestInvocation(
                "test", "noAnnotation", null, "/path",
                Map.of(), null);

        assertThat(invocation.httpMethod()).isNull();
    }
}
