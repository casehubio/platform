package io.casehub.platform.graphql;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GraphQLErrorTest {

    @Test
    void buildsFromTypeAndDetail() {
        var error = new GraphQLError(
                URI.create("urn:casehub:error:not-found"),
                "Not Found",
                404,
                "Case with ID 123 not found",
                null
        );
        assertThat(error.type()).isEqualTo(URI.create("urn:casehub:error:not-found"));
        assertThat(error.title()).isEqualTo("Not Found");
        assertThat(error.status()).isEqualTo(404);
        assertThat(error.detail()).isEqualTo("Case with ID 123 not found");
        assertThat(error.extensions()).isNull();
    }

    @Test
    void carriesExtensions() {
        var extensions = Map.<String, Object>of("traceId", "abc-123", "field", "caseId");
        var error = new GraphQLError(
                URI.create("urn:casehub:error:validation"),
                "Validation Error",
                400,
                "caseId must not be null",
                extensions
        );
        assertThat(error.extensions()).containsEntry("traceId", "abc-123");
        assertThat(error.extensions()).containsEntry("field", "caseId");
    }

    @Test
    void statusNullableForNonHttpErrors() {
        var error = new GraphQLError(
                URI.create("urn:casehub:error:internal"),
                "Internal Error",
                null,
                "Unexpected failure",
                null
        );
        assertThat(error.status()).isNull();
    }
}
