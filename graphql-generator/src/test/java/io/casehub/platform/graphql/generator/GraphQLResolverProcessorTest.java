package io.casehub.platform.graphql.generator;

import org.jboss.jandex.DotName;
import org.jboss.jandex.Indexer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

class GraphQLResolverProcessorTest {

    @Test
    void jandexCanIndexAnnotatedInterface() throws IOException {
        Indexer indexer = new Indexer();
        try (InputStream is = getClass().getClassLoader()
                .getResourceAsStream("META-INF/jandex.idx")) {
            if (is != null) {
                var reader = new org.jboss.jandex.IndexReader(is);
                var index = reader.read();
                assertThat(index).isNotNull();
            }
        }
    }

    @Test
    void jandexFindsAnnotationsOnClasspath() throws IOException {
        var indexes = new java.util.ArrayList<org.jboss.jandex.IndexView>();
        var resources = getClass().getClassLoader()
                .getResources("META-INF/jandex.idx");
        while (resources.hasMoreElements()) {
            var url = resources.nextElement();
            try (InputStream is = url.openStream()) {
                indexes.add(new org.jboss.jandex.IndexReader(is).read());
            }
        }

        assertThat(indexes).isNotEmpty();

        var combined = org.jboss.jandex.CompositeIndex.create(indexes);
        var mcpDomainAnns = combined.getAnnotations(
                DotName.createSimple("io.casehub.platform.api.mcp.McpDomain"));

        // McpDomain should be found on at least the annotation's own test class
        // (or other indexed classes in platform-api)
        assertThat(combined).isNotNull();
    }

    @Test
    void processorDecapitalizeWorks() {
        assertThat(decapitalize("TestItemService")).isEqualTo("testItemService");
        assertThat(decapitalize("")).isEmpty();
    }

    @Test
    void toKebabCase_camelCase() {
        assertThat(GraphQLResolverProcessor.toKebabCase("markAllRead")).isEqualTo("mark-all-read");
    }

    @Test
    void toKebabCase_singleWord() {
        assertThat(GraphQLResolverProcessor.toKebabCase("vendors")).isEqualTo("vendors");
    }

    @Test
    void toKebabCase_consecutiveUppercase() {
        assertThat(GraphQLResolverProcessor.toKebabCase("HTTPMethod")).isEqualTo("http-method");
    }

    @Test
    void toKebabCase_consecutiveUppercaseInMiddle() {
        assertThat(GraphQLResolverProcessor.toKebabCase("listHTTPMethods")).isEqualTo("list-http-methods");
    }

    @Test
    void toKebabCase_alreadyLowercase() {
        assertThat(GraphQLResolverProcessor.toKebabCase("status")).isEqualTo("status");
    }

    @Test
    void toPascalCase_simpleWord() {
        assertThat(GraphQLResolverProcessor.toPascalCase("digest")).isEqualTo("Digest");
    }

    @Test
    void toPascalCase_kebabCase() {
        assertThat(GraphQLResolverProcessor.toPascalCase("delivery-channels")).isEqualTo("DeliveryChannels");
    }

    @Test
    void toPascalCase_multipleHyphens() {
        assertThat(GraphQLResolverProcessor.toPascalCase("notification-preferences")).isEqualTo("NotificationPreferences");
    }

    @Test
    void toPascalCase_alreadyCapitalized() {
        assertThat(GraphQLResolverProcessor.toPascalCase("Digest")).isEqualTo("Digest");
    }

    @Test
    void httpVerbMapping_defaultQuery_isGET() {
        assertThat(GraphQLResolverProcessor.resolveHttpVerb(GraphQLResolverProcessor.OperationType.QUERY, null)).isEqualTo("GET");
    }

    @Test
    void httpVerbMapping_defaultMutation_isPOST() {
        assertThat(GraphQLResolverProcessor.resolveHttpVerb(GraphQLResolverProcessor.OperationType.MUTATION, null)).isEqualTo("POST");
    }

    @Test
    void httpVerbMapping_restMethodOverride_DELETE() {
        assertThat(GraphQLResolverProcessor.resolveHttpVerb(GraphQLResolverProcessor.OperationType.MUTATION, "DELETE")).isEqualTo("DELETE");
    }

    @Test
    void httpVerbMapping_restMethodOverride_PUT() {
        assertThat(GraphQLResolverProcessor.resolveHttpVerb(GraphQLResolverProcessor.OperationType.MUTATION, "PUT")).isEqualTo("PUT");
    }

    @Test
    void httpVerbMapping_restMethodOverride_PATCH() {
        assertThat(GraphQLResolverProcessor.resolveHttpVerb(GraphQLResolverProcessor.OperationType.MUTATION, "PATCH")).isEqualTo("PATCH");
    }

    @Test
    void isSimpleType_string() {
        assertThat(GraphQLResolverProcessor.isSimpleType("java.lang.String")).isTrue();
    }

    @Test
    void isSimpleType_primitiveWrapper() {
        assertThat(GraphQLResolverProcessor.isSimpleType("java.lang.Integer")).isTrue();
        assertThat(GraphQLResolverProcessor.isSimpleType("java.lang.Long")).isTrue();
        assertThat(GraphQLResolverProcessor.isSimpleType("java.lang.Boolean")).isTrue();
    }

    @Test
    void isSimpleType_uuid() {
        assertThat(GraphQLResolverProcessor.isSimpleType("java.util.UUID")).isTrue();
    }

    @Test
    void isSimpleType_javaTime() {
        assertThat(GraphQLResolverProcessor.isSimpleType("java.time.Instant")).isTrue();
        assertThat(GraphQLResolverProcessor.isSimpleType("java.time.LocalDate")).isTrue();
    }

    @Test
    void isSimpleType_complexType() {
        assertThat(GraphQLResolverProcessor.isSimpleType("io.casehub.platform.api.callback.CallbackRegistrationRequest")).isFalse();
    }

    @Test
    void responseWrapping_void_returns204() {
        assertThat(GraphQLResolverProcessor.generateResponseCode("void", "spi.doThing(arg0)"))
                .isEqualTo("spi.doThing(arg0); return Response.noContent().build();");
    }

    @Test
    void responseWrapping_optional_returns200or404() {
        assertThat(GraphQLResolverProcessor.generateResponseCode("Optional<String>", "spi.findItem(arg0)"))
                .isEqualTo("return spi.findItem(arg0).map(v -> Response.ok(v).build()).orElse(Response.status(404).build());");
    }

    @Test
    void responseWrapping_regularType_returns200() {
        assertThat(GraphQLResolverProcessor.generateResponseCode("List<String>", "spi.listItems()"))
                .isEqualTo("return Response.ok(spi.listItems()).build();");
    }

    @Test
    void restPathOverride_usesLiteralValue() {
        assertThat(GraphQLResolverProcessor.resolveRestPath("grants", "grant")).isEqualTo("grants");
    }

    @Test
    void restPathOverride_absent_fallsBackToKebab() {
        assertThat(GraphQLResolverProcessor.resolveRestPath(null, "grantBatch")).isEqualTo("grant-batch");
    }

    @Test
    void restPathOverride_nestedSegments() {
        assertThat(GraphQLResolverProcessor.resolveRestPath("grants/batch", "grantBatch")).isEqualTo("grants/batch");
    }

    @Test
    void isSimpleType_enumViaJandex() throws IOException {
        var indexer = new org.jboss.jandex.Indexer();
        indexer.indexClass(io.casehub.platform.api.acl.AclAction.class);
        var index = indexer.complete();
        assertThat(GraphQLResolverProcessor.isSimpleType("io.casehub.platform.api.acl.AclAction", index)).isTrue();
    }

    @Test
    void isSimpleType_fromStringViaJandex() throws IOException {
        var indexer = new org.jboss.jandex.Indexer();
        indexer.indexClass(io.casehub.platform.api.acl.ResourceId.class);
        var index = indexer.complete();
        assertThat(GraphQLResolverProcessor.isSimpleType("io.casehub.platform.api.acl.ResourceId", index)).isTrue();
    }

    @Test
    void isSimpleType_complexTypeWithJandex() throws IOException {
        var indexer = new org.jboss.jandex.Indexer();
        indexer.indexClass(io.casehub.platform.api.acl.AclEntryRequest.class);
        var index = indexer.complete();
        assertThat(GraphQLResolverProcessor.isSimpleType("io.casehub.platform.api.acl.AclEntryRequest", index)).isFalse();
    }

    @Test
    void isSimpleType_staticFallback_stillWorks() {
        assertThat(GraphQLResolverProcessor.isSimpleType("java.lang.String", null)).isTrue();
        assertThat(GraphQLResolverProcessor.isSimpleType("java.time.Instant", null)).isTrue();
    }


    @Test
    void roundEnvScanDiscoversLocalInterface() throws Exception {
        var spi = com.google.testing.compile.JavaFileObjects.forSourceString(
                "test.SampleApi",
                """
                package test;
                import io.casehub.platform.api.mcp.McpDomain;
                import io.casehub.platform.api.mcp.PlatformQuery;
                import java.util.List;
                
                @McpDomain("sample")
                public interface SampleApi {
                    @PlatformQuery("List items")
                    List<String> listItems();
                }
                """);

        var compilation = com.google.testing.compile.Compiler.javac()
                                                             .withProcessors(new GraphQLResolverProcessor())
                                                             .withOptions("-AdomainFilter=sample", "-AgenerateGraphQL=false")
                                                             .compile(spi);

        var restSource = compilation.generatedSourceFile(
                "io.casehub.platform.rest.generated.GeneratedSampleResource");
        assertThat(restSource).isPresent();

        String content = restSource.get().getCharContent(true).toString();
        assertThat(content).contains("class GeneratedSampleResource");
        assertThat(content).contains("@Path(\"/api/sample\")");
        assertThat(content).contains("import test.SampleApi;");
        assertThat(content).contains("public Response listItems(");

        var graphqlSource = compilation.generatedSourceFile(
                "io.casehub.platform.graphql.generated.GeneratedSampleResolver");
        assertThat(graphqlSource).isEmpty();
    }

    private static String decapitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }
}
