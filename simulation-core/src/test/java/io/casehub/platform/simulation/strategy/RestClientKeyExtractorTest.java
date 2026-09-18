package io.casehub.platform.simulation.strategy;

import io.casehub.platform.simulation.RestInvocation;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RestClientKeyExtractorTest {

    private final RestClientKeyExtractor extractor = new RestClientKeyExtractor();

    @Test
    void extractsHttpMethodAndResolvedPath() {
        var invocation = new RestInvocation(
                "scim", "membersOf", "GET", "/Groups/{id}/Members",
                Map.of("id", "grp-1"), null);

        assertThat(extractor.extract(invocation))
                .isEqualTo("GET /Groups/grp-1/Members");
    }

    @Test
    void multiplePathParamsSubstituted() {
        var invocation = new RestInvocation(
                "github", "getFile", "GET", "/repos/{owner}/{repo}/contents/{path}",
                Map.of("owner", "casehubio", "repo", "platform", "path", "README.md"),
                null);

        assertThat(extractor.extract(invocation))
                .isEqualTo("GET /repos/casehubio/platform/contents/README.md");
    }

    @Test
    void nullHttpMethodHandledGracefully() {
        var invocation = new RestInvocation(
                "test", "noAnnotation", null, "/path",
                Map.of(), null);

        assertThat(extractor.extract(invocation))
                .isEqualTo("null /path");
    }

    @Test
    void emptyParamsPreservesTemplate() {
        var invocation = new RestInvocation(
                "test", "list", "GET", "/items",
                Map.of(), null);

        assertThat(extractor.extract(invocation))
                .isEqualTo("GET /items");
    }

    @Test
    void queryParamsNotSubstitutedIntoPath() {
        var invocation = new RestInvocation(
                "scim", "getGroup", "GET", "/Groups/{id}",
                Map.of("id", "grp-1", "attributes", "members"),
                null);

        assertThat(extractor.extract(invocation))
                .isEqualTo("GET /Groups/grp-1");
    }
}
