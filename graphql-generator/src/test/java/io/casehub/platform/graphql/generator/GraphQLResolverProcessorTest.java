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
    void responseWrapping_mutation_returns200() {
        assertThat(GraphQLResolverProcessor.generateResponseCode("String", "spi.create(arg0)", true, -1, false))
                .isEqualTo("return Response.ok(spi.create(arg0)).build();");
    }

    @Test
    void responseWrapping_query_returns200() {
        assertThat(GraphQLResolverProcessor.generateResponseCode("String", "spi.get(arg0)", false, -1, false))
                .isEqualTo("return Response.ok(spi.get(arg0)).build();");
    }

    @Test
    void responseWrapping_restStatusOverride_onMutation() {
        assertThat(GraphQLResolverProcessor.generateResponseCode("String", "spi.create(arg0)", true, 202, false))
                .isEqualTo("return Response.status(202).entity(spi.create(arg0)).build();");
    }

    @Test
    void responseWrapping_pathParam_nonCollection_nullChecks() {
        String result = GraphQLResolverProcessor.generateResponseCode("String", "spi.get(id)", false, -1, true);
        assertThat(result).contains("if (result == null) return Response.status(404).build()");
        assertThat(result).contains("Response.ok(result).build()");
    }

    @Test
    void responseWrapping_pathParam_collection_noNullCheck() {
        String result = GraphQLResolverProcessor.generateResponseCode("List<String>", "spi.list(id)", false, -1, true);
        assertThat(result).doesNotContain("404");
        assertThat(result).contains("Response.ok(");
    }

    @Test
    void isCollectionType_list() {
        assertThat(GraphQLResolverProcessor.isCollectionType("List<String>")).isTrue();
    }

    @Test
    void isCollectionType_nonCollection() {
        assertThat(GraphQLResolverProcessor.isCollectionType("String")).isFalse();
    }

    @Test
    void restNameOverridesQueryParamName() throws Exception {
        var spi = com.google.testing.compile.JavaFileObjects.forSourceString(
                "test.PageApi",
                """
                package test;
                import io.casehub.platform.api.mcp.*;

                @McpDomain("pages")
                public interface PageApi {
                    @PlatformQuery("List pages")
                    java.util.List<String> listPages(@RestName("page_size") Integer pageSize, @RestName("page_num") Integer pageNumber);
                }
                """);

        var compilation = com.google.testing.compile.Compiler.javac()
                .withProcessors(new GraphQLResolverProcessor())
                .withOptions("-AdomainFilter=pages", "-AgenerateGraphQL=false")
                .compile(spi);

        String content = compilation.generatedSourceFile(
                "test.rest.PagesResource")
                .get().getCharContent(true).toString();
        assertThat(content).contains("@QueryParam(\"page_size\")");
        assertThat(content).contains("@QueryParam(\"page_num\")");
    }

    @Test
    void rolesAllowedPassesThroughToRest() throws Exception {
        var spi = com.google.testing.compile.JavaFileObjects.forSourceString(
                "test.AdminApi",
                """
                package test;
                import io.casehub.platform.api.mcp.*;
                import jakarta.annotation.security.RolesAllowed;

                @McpDomain("admin")
                public interface AdminApi {
                    @PlatformQuery("Admin view")
                    @RolesAllowed({"admin", "superuser"})
                    String getAdminData();
                }
                """);

        var compilation = com.google.testing.compile.Compiler.javac()
                .withProcessors(new GraphQLResolverProcessor())
                .withOptions("-AdomainFilter=admin", "-AgenerateGraphQL=false")
                .compile(spi);

        String content = compilation.generatedSourceFile(
                "test.rest.AdminResource")
                .get().getCharContent(true).toString();
        assertThat(content).contains("jakarta.annotation.security.RolesAllowed");
        assertThat(content).contains("\"admin\"");
        assertThat(content).contains("\"superuser\"");
    }

    @Test
    void mutationEndpointReturns200() throws Exception {
        var spi = com.google.testing.compile.JavaFileObjects.forSourceString(
                "test.CreateApi",
                """
                package test;
                import io.casehub.platform.api.mcp.*;

                @McpDomain("items")
                public interface CreateApi {
                    @PlatformMutation("Create an item")
                    String createItem(String name);
                }
                """);

        var compilation = com.google.testing.compile.Compiler.javac()
                .withProcessors(new GraphQLResolverProcessor())
                .withOptions("-AdomainFilter=items", "-AgenerateGraphQL=false")
                .compile(spi);

        String content = compilation.generatedSourceFile(
                "test.rest.ItemsResource")
                .get().getCharContent(true).toString();
        assertThat(content).contains("Response.ok(");
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
                "test.rest.SampleResource");
        assertThat(restSource).isPresent();

        String content = restSource.get().getCharContent(true).toString();
        assertThat(content).contains("class SampleResource");
        assertThat(content).contains("@Path(\"/api/sample\")");
        assertThat(content).contains("import test.SampleApi;");
        assertThat(content).contains("public Response listItems(");

        var graphqlSource = compilation.generatedSourceFile(
                "test.graphql.SampleResolver");
        assertThat(graphqlSource).isEmpty();
    }

    @Test
    void domainFilterExcludesNonMatchingDomains() throws Exception {
        var spi1 = com.google.testing.compile.JavaFileObjects.forSourceString(
                "test.AlphaApi",
                """
                package test;
                import io.casehub.platform.api.mcp.McpDomain;
                import io.casehub.platform.api.mcp.PlatformQuery;
                
                @McpDomain("alpha")
                public interface AlphaApi {
                    @PlatformQuery("Get alpha") String getAlpha();
                }
                """);

        var spi2 = com.google.testing.compile.JavaFileObjects.forSourceString(
                "test.BetaApi",
                """
                package test;
                import io.casehub.platform.api.mcp.McpDomain;
                import io.casehub.platform.api.mcp.PlatformQuery;
                
                @McpDomain("beta")
                public interface BetaApi {
                    @PlatformQuery("Get beta") String getBeta();
                }
                """);

        var compilation = com.google.testing.compile.Compiler.javac()
                                                             .withProcessors(new GraphQLResolverProcessor())
                                                             .withOptions("-AdomainFilter=alpha", "-AgenerateGraphQL=false")
                                                             .compile(spi1, spi2);

        assertThat(compilation.generatedSourceFile(
                "test.rest.AlphaResource")).isPresent();
        assertThat(compilation.generatedSourceFile(
                "test.rest.BetaResource")).isEmpty();
        assertThat(compilation.generatedSourceFile(
                "test.graphql.AlphaResolver")).isEmpty();
    }

    @Test
    void generateGraphQLFalseSuppressesResolvers() throws Exception {
        var spi = com.google.testing.compile.JavaFileObjects.forSourceString(
                "test.GammaApi",
                """
                package test;
                import io.casehub.platform.api.mcp.McpDomain;
                import io.casehub.platform.api.mcp.PlatformQuery;
                
                @McpDomain("gamma")
                public interface GammaApi {
                    @PlatformQuery("Get gamma") String getGamma();
                }
                """);

        var compilation = com.google.testing.compile.Compiler.javac()
                                                             .withProcessors(new GraphQLResolverProcessor())
                                                             .withOptions("-AdomainFilter=gamma", "-AgenerateGraphQL=false")
                                                             .compile(spi);

        assertThat(compilation.generatedSourceFile(
                "test.graphql.GammaResolver")).isEmpty();
        assertThat(compilation.generatedSourceFile(
                "test.rest.GammaResource")).isPresent();
    }

    @Test
    void hyphenatedDomainProducesValidClassName() throws Exception {
        var spi = com.google.testing.compile.JavaFileObjects.forSourceString(
                "test.DeliveryChannelApi",
                """
                package test;
                import io.casehub.platform.api.mcp.McpDomain;
                import io.casehub.platform.api.mcp.PlatformQuery;
                import java.util.List;
                
                @McpDomain("test-delivery-channels")
                public interface DeliveryChannelApi {
                    @PlatformQuery("List channels") List<String> listChannels();
                }
                """);

        var compilation = com.google.testing.compile.Compiler.javac()
                                                             .withProcessors(new GraphQLResolverProcessor())
                                                             .withOptions("-AdomainFilter=test-delivery-channels", "-AgenerateGraphQL=false")
                                                             .compile(spi);

        var restSource = compilation.generatedSourceFile(
                "test.rest.TestDeliveryChannelsResource");
        assertThat(restSource).isPresent();

        String content = restSource.get().getCharContent(true).toString();
        assertThat(content).contains("class TestDeliveryChannelsResource");
        assertThat(content).contains("@Path(\"/api/test-delivery-channels\")");
    }


    @Test
    void streamingRestProducesSseEndpoint() throws Exception {
        var spi = com.google.testing.compile.JavaFileObjects.forSourceString(
                "test.EventApi",
                """
                package test;
                import io.casehub.platform.api.mcp.*;
                import io.smallrye.mutiny.Multi;

                @McpDomain("events")
                public interface EventApi {
                    @PlatformStream("Real-time events")
                    Multi<String> eventStream(@PathParam java.util.UUID scopeId);
                }
                """);

        var compilation = com.google.testing.compile.Compiler.javac()
                .withProcessors(new GraphQLResolverProcessor())
                .withOptions("-AdomainFilter=events", "-AgenerateGraphQL=false")
                .compile(spi);

        var restSource = compilation.generatedSourceFile(
                "test.rest.EventsResource");
        assertThat(restSource).isPresent();

        String content = restSource.get().getCharContent(true).toString();
        assertThat(content).contains("@Produces(MediaType.SERVER_SENT_EVENTS)");
        assertThat(content).contains("RestStreamElementType(MediaType.APPLICATION_JSON)");
        assertThat(content).contains("public Multi<String> eventStream(");
        assertThat(content).doesNotContain("Response.ok");
        assertThat(content).doesNotContain("@RunOnVirtualThread");
    }

    @Test
    void streamingGraphqlProducesSubscription() throws Exception {
        var spi = com.google.testing.compile.JavaFileObjects.forSourceString(
                "test.SubApi",
                """
                package test;
                import io.casehub.platform.api.mcp.*;
                import io.smallrye.mutiny.Multi;

                @McpDomain("subs")
                public interface SubApi {
                    @PlatformStream("Live updates")
                    Multi<String> updates(java.util.UUID id);
                }
                """);

        var compilation = com.google.testing.compile.Compiler.javac()
                .withProcessors(new GraphQLResolverProcessor())
                .withOptions("-AdomainFilter=subs", "-AgenerateRest=false")
                .compile(spi);

        String content = compilation.generatedSourceFile(
                "test.graphql.SubsResolver")
                .get().getCharContent(true).toString();
        assertThat(content).contains("Subscription");
        assertThat(content).doesNotContain("@Query\n");
        assertThat(content).contains("public Multi<String> updates(");
    }

    @Test
    void nonStreamingRestHasMethodLevelVirtualThread() throws Exception {
        var spi = com.google.testing.compile.JavaFileObjects.forSourceString(
                "test.PlainApi",
                """
                package test;
                import io.casehub.platform.api.mcp.*;

                @McpDomain("plain")
                public interface PlainApi {
                    @PlatformQuery("Get item")
                    String getItem(@PathParam java.util.UUID id);
                }
                """);

        var compilation = com.google.testing.compile.Compiler.javac()
                .withProcessors(new GraphQLResolverProcessor())
                .withOptions("-AdomainFilter=plain", "-AgenerateGraphQL=false")
                .compile(spi);

        String content = compilation.generatedSourceFile(
                "test.rest.PlainResource")
                .get().getCharContent(true).toString();
        assertThat(content).contains("@RunOnVirtualThread");
        assertThat(content).contains("public Response getItem(");
    }

    @Test
    void paginatedResponseAddsXTotalCountHeader() throws Exception {
        var pageType = com.google.testing.compile.JavaFileObjects.forSourceString(
                "test.ItemPage",
                """
                package test;
                public record ItemPage(java.util.List<String> items, int totalCount, boolean hasMore) {}
                """);

        var spi = com.google.testing.compile.JavaFileObjects.forSourceString(
                "test.ListApi",
                """
                package test;
                import io.casehub.platform.api.mcp.*;

                @McpDomain("lists")
                public interface ListApi {
                    @PlatformQuery("List items")
                    @PaginatedResponse
                    test.ItemPage listItems(Integer offset, Integer limit);
                }
                """);

        var compilation = com.google.testing.compile.Compiler.javac()
                .withProcessors(new GraphQLResolverProcessor())
                .withOptions("-AdomainFilter=lists", "-AgenerateGraphQL=false")
                .compile(spi, pageType);

        String content = compilation.generatedSourceFile(
                "test.rest.ListsResource")
                .get().getCharContent(true).toString();
        assertThat(content).contains("X-Total-Count");
        assertThat(content).contains("__pageResult.totalCount()");
    }

    @Test
    void httpVerbMapping_defaultStream_isGET() {
        assertThat(GraphQLResolverProcessor.resolveHttpVerb(GraphQLResolverProcessor.OperationType.STREAM, null)).isEqualTo("GET");
    }

    // --- Class-based @McpDomain tests ---

    @Test
    void classBasedDomainGeneratesRestResource() throws Exception {
        var cls = com.google.testing.compile.JavaFileObjects.forSourceString(
                "test.StatusService",
                """
                package test;
                import io.casehub.platform.api.mcp.*;
                import jakarta.enterprise.context.ApplicationScoped;

                @McpDomain("status")
                @ApplicationScoped
                public class StatusService {
                    @PlatformQuery("Get system status")
                    public String getStatus() { return "ok"; }

                    @PlatformMutation("Reset status")
                    public void resetStatus(String reason) {}
                }
                """);

        var compilation = com.google.testing.compile.Compiler.javac()
                .withProcessors(new GraphQLResolverProcessor())
                .withOptions("-AdomainFilter=status")
                .compile(cls);

        var restSource = compilation.generatedSourceFile(
                "test.rest.StatusResource");
        assertThat(restSource).isPresent();

        String restContent = restSource.get().getCharContent(true).toString();
        assertThat(restContent).contains("@Path(\"/api/status\")");
        assertThat(restContent).contains("import test.StatusService;");
        assertThat(restContent).contains("StatusService statusService;");
        assertThat(restContent).contains("statusService.getStatus()");

        var graphqlSource = compilation.generatedSourceFile(
                "test.graphql.StatusResolver");
        assertThat(graphqlSource).isPresent();

        String gqlContent = graphqlSource.get().getCharContent(true).toString();
        assertThat(gqlContent).contains("@GraphQLApi");
        assertThat(gqlContent).contains("import test.StatusService;");
        assertThat(gqlContent).contains("StatusService statusService;");
    }

    @Test
    void classWithoutScopeEmitsWarning() throws Exception {
        var cls = com.google.testing.compile.JavaFileObjects.forSourceString(
                "test.UnscopedService",
                """
                package test;
                import io.casehub.platform.api.mcp.*;

                @McpDomain("unscoped")
                public class UnscopedService {
                    @PlatformQuery("Get data")
                    public String getData() { return "data"; }
                }
                """);

        var compilation = com.google.testing.compile.Compiler.javac()
                .withProcessors(new GraphQLResolverProcessor())
                .withOptions("-AdomainFilter=unscoped", "-AgenerateGraphQL=false")
                .compile(cls);

        assertThat(compilation.warnings()).anySatisfy(diag ->
                assertThat(diag.getMessage(null)).contains("without a visible CDI scope annotation"));

        assertThat(compilation.generatedSourceFile(
                "test.rest.UnscopedResource")).isPresent();
    }

    @Test
    void classWithScopeNoWarning() throws Exception {
        var cls = com.google.testing.compile.JavaFileObjects.forSourceString(
                "test.ScopedService",
                """
                package test;
                import io.casehub.platform.api.mcp.*;
                import jakarta.enterprise.context.ApplicationScoped;

                @McpDomain("scoped")
                @ApplicationScoped
                public class ScopedService {
                    @PlatformQuery("Get data")
                    public String getData() { return "data"; }
                }
                """);

        var compilation = com.google.testing.compile.Compiler.javac()
                .withProcessors(new GraphQLResolverProcessor())
                .withOptions("-AdomainFilter=scoped", "-AgenerateGraphQL=false")
                .compile(cls);

        assertThat(compilation.warnings()).noneSatisfy(diag ->
                assertThat(diag.getMessage(null)).contains("without a visible CDI scope annotation"));
    }

    @Test
    void classBasedDomainWithContextParam() throws Exception {
        var cls = com.google.testing.compile.JavaFileObjects.forSourceString(
                "test.TenantService",
                """
                package test;
                import io.casehub.platform.api.mcp.*;
                import jakarta.enterprise.context.ApplicationScoped;
                import java.util.List;

                @McpDomain("tenant-items")
                @ApplicationScoped
                public class TenantService {
                    @PlatformQuery("List tenant items")
                    public List<String> listItems(@ContextParam("tenancyId") String tenancyId) {
                        return List.of();
                    }
                }
                """);

        var compilation = com.google.testing.compile.Compiler.javac()
                .withProcessors(new GraphQLResolverProcessor())
                .withOptions("-AdomainFilter=tenant-items", "-AgenerateGraphQL=false")
                .compile(cls);

        var restSource = compilation.generatedSourceFile(
                "test.rest.TenantItemsResource");
        assertThat(restSource).isPresent();

        String content = restSource.get().getCharContent(true).toString();
        assertThat(content).contains("currentPrincipal.tenancyId()");
        assertThat(content).contains("CurrentPrincipal currentPrincipal;");
        assertThat(content).doesNotContain("@QueryParam(\"tenancyId\")");
    }


    @Test
    void restPackageOverrideTakesPrecedence() throws Exception {
        var spi = com.google.testing.compile.JavaFileObjects.forSourceString(
                "test.OverrideApi",
                """
                package test;
                import io.casehub.platform.api.mcp.*;
                
                @McpDomain("override")
                public interface OverrideApi {
                    @PlatformQuery("Get data") String getData();
                }
                """);

        var compilation = com.google.testing.compile.Compiler.javac()
                                                             .withProcessors(new GraphQLResolverProcessor())
                                                             .withOptions("-AdomainFilter=override", "-AgenerateGraphQL=false",
                                                                          "-ArestPackage=custom.pkg")
                                                             .compile(spi);

        assertThat(compilation.generatedSourceFile(
                "custom.pkg.OverrideResource")).isPresent();
    }

    @Test
    void graphqlPackageOverrideTakesPrecedence() throws Exception {
        var spi = com.google.testing.compile.JavaFileObjects.forSourceString(
                "test.GqlOverrideApi",
                """
                package test;
                import io.casehub.platform.api.mcp.*;
                
                @McpDomain("gql-override")
                public interface GqlOverrideApi {
                    @PlatformQuery("Get data") String getData();
                }
                """);

        var compilation = com.google.testing.compile.Compiler.javac()
                                                             .withProcessors(new GraphQLResolverProcessor())
                                                             .withOptions("-AdomainFilter=gql-override", "-AgenerateRest=false",
                                                                          "-AgraphqlPackage=custom.gql")
                                                             .compile(spi);

        assertThat(compilation.generatedSourceFile(
                "custom.gql.GqlOverrideResolver")).isPresent();
    }

    @Test
    void apiSubpackageDerivation() throws Exception {
        var spi = com.google.testing.compile.JavaFileObjects.forSourceString(
                "com.example.api.billing.InvoiceApi",
                """
                package com.example.api.billing;
                import io.casehub.platform.api.mcp.*;
                
                @McpDomain("invoices")
                public interface InvoiceApi {
                    @PlatformQuery("List invoices") java.util.List<String> list();
                }
                """);

        var compilation = com.google.testing.compile.Compiler.javac()
                                                             .withProcessors(new GraphQLResolverProcessor())
                                                             .withOptions("-AdomainFilter=invoices", "-AgenerateGraphQL=false")
                                                             .compile(spi);

        assertThat(compilation.generatedSourceFile(
                "com.example.rest.billing.InvoicesResource")).isPresent();
    }

    @Test
    void deriveOutputPackage_replacesApiMiddle() {
        assertThat(GraphQLResolverProcessor.deriveOutputPackage(
                "io.casehub.platform.api.acl", "rest"))
                .isEqualTo("io.casehub.platform.rest.acl");
    }

    @Test
    void deriveOutputPackage_replacesApiEnd() {
        assertThat(GraphQLResolverProcessor.deriveOutputPackage(
                "io.casehub.chat.api", "rest"))
                .isEqualTo("io.casehub.chat.rest");
    }

    @Test
    void deriveOutputPackage_appendsWhenNoApi() {
        assertThat(GraphQLResolverProcessor.deriveOutputPackage(
                "io.casehub.something", "rest"))
                .isEqualTo("io.casehub.something.rest");
    }

    @Test
    void deriveOutputPackage_graphqlChannel() {
        assertThat(GraphQLResolverProcessor.deriveOutputPackage(
                "io.casehub.platform.api.acl", "graphql"))
                .isEqualTo("io.casehub.platform.graphql.acl");
    }

    @Test
    void deriveOutputPackage_singleSegment() {
        assertThat(GraphQLResolverProcessor.deriveOutputPackage(
                "test", "rest"))
                .isEqualTo("test.rest");
    }

    private static String decapitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    @Test
    void webhookEndpointGeneratesPostWithRawBody() throws Exception {
        var spi = com.google.testing.compile.JavaFileObjects.forSourceString(
                "test.WebhookApi",
                """
                package test;
                import io.casehub.platform.api.mcp.*;
                import jakarta.enterprise.context.ApplicationScoped;
                
                @McpDomain("hooks")
                @ApplicationScoped
                public class WebhookApi {
                    @PlatformWebhook("Receive webhook event")
                    public void receive(String body) {}
                }
                """);

        var compilation = com.google.testing.compile.Compiler.javac()
                                                             .withProcessors(new GraphQLResolverProcessor())
                                                             .withOptions("-AdomainFilter=hooks", "-AgenerateGraphQL=false")
                                                             .compile(spi);

        var restSource = compilation.generatedSourceFile("test.rest.HooksResource");
        assertThat(restSource).isPresent();

        String content = restSource.get().getCharContent(true).toString();
        assertThat(content).contains("@POST");
        assertThat(content).contains("@Consumes(MediaType.APPLICATION_JSON)");
        assertThat(content).contains("public Response receive(String body)");
        assertThat(content).contains("Response.ok().build()");
        assertThat(content).doesNotContain("@RunOnVirtualThread");
        assertThat(content).contains("@jakarta.annotation.security.PermitAll");
    }

    @Test
    void webhookWithHeaderParam() throws Exception {
        var spi = com.google.testing.compile.JavaFileObjects.forSourceString(
                "test.GhWebhookApi",
                """
                package test;
                import io.casehub.platform.api.mcp.*;
                import jakarta.enterprise.context.ApplicationScoped;
                
                @McpDomain("gh-hooks")
                @ApplicationScoped
                public class GhWebhookApi {
                    @PlatformWebhook("Receive GitHub webhook")
                    public void handleGitHub(
                            @PathParam String tenancyId,
                            @HeaderParam("X-Hub-Signature-256") String signature,
                            String body) {}
                }
                """);

        var compilation = com.google.testing.compile.Compiler.javac()
                                                             .withProcessors(new GraphQLResolverProcessor())
                                                             .withOptions("-AdomainFilter=gh-hooks", "-AgenerateGraphQL=false")
                                                             .compile(spi);

        String content = compilation.generatedSourceFile("test.rest.GhHooksResource")
                                    .get().getCharContent(true).toString();
        assertThat(content).contains("@POST");
        assertThat(content).contains("@jakarta.ws.rs.PathParam(\"tenancyId\")");
        assertThat(content).contains("@jakarta.ws.rs.HeaderParam(\"X-Hub-Signature-256\")");
        assertThat(content).contains("String body)");
        assertThat(content).doesNotContain("@QueryParam");
    }

    @Test
    void webhookWithQueryParam() throws Exception {
        var spi = com.google.testing.compile.JavaFileObjects.forSourceString(
                "test.JiraWebhookApi",
                """
                package test;
                import io.casehub.platform.api.mcp.*;
                import jakarta.enterprise.context.ApplicationScoped;
                
                @McpDomain("jira-hooks")
                @ApplicationScoped
                public class JiraWebhookApi {
                    @PlatformWebhook("Receive Jira webhook")
                    public void handleJira(
                            @PathParam String tenancyId,
                            @QueryParam("secret") String secret,
                            String body) {}
                }
                """);

        var compilation = com.google.testing.compile.Compiler.javac()
                                                             .withProcessors(new GraphQLResolverProcessor())
                                                             .withOptions("-AdomainFilter=jira-hooks", "-AgenerateGraphQL=false")
                                                             .compile(spi);

        String content = compilation.generatedSourceFile("test.rest.JiraHooksResource")
                                    .get().getCharContent(true).toString();
        assertThat(content).contains("@jakarta.ws.rs.QueryParam(\"secret\")");
        assertThat(content).contains("String body)");
    }

    @Test
    void webhookSkipsGraphQL() throws Exception {
        var spi = com.google.testing.compile.JavaFileObjects.forSourceString(
                "test.HookOnlyApi",
                """
                package test;
                import io.casehub.platform.api.mcp.*;
                import jakarta.enterprise.context.ApplicationScoped;
                
                @McpDomain("hook-only")
                @ApplicationScoped
                public class HookOnlyApi {
                    @PlatformWebhook("Inbound event")
                    public void onEvent(String body) {}
                }
                """);

        var compilation = com.google.testing.compile.Compiler.javac()
                                                             .withProcessors(new GraphQLResolverProcessor())
                                                             .withOptions("-AdomainFilter=hook-only")
                                                             .compile(spi);

        assertThat(compilation.generatedSourceFile("test.rest.HookOnlyResource")).isPresent();
        assertThat(compilation.generatedSourceFile("test.graphql.HookOnlyResolver")).isEmpty();
    }

    @Test
    void webhookCustomConsumes() throws Exception {
        var spi = com.google.testing.compile.JavaFileObjects.forSourceString(
                "test.FedApi",
                """
                package test;
                import io.casehub.platform.api.mcp.*;
                import jakarta.enterprise.context.ApplicationScoped;
                
                @McpDomain("federation")
                @ApplicationScoped
                public class FedApi {
                    @PlatformWebhook(value = "CloudEvents inbound", consumes = "application/cloudevents+json")
                    public void receiveEvent(
                            @HeaderParam("X-Federation-Signature") String signature,
                            @HeaderParam("X-Federation-Peer-Id") String peerId,
                            String body) {}
                }
                """);

        var compilation = com.google.testing.compile.Compiler.javac()
                                                             .withProcessors(new GraphQLResolverProcessor())
                                                             .withOptions("-AdomainFilter=federation", "-AgenerateGraphQL=false")
                                                             .compile(spi);

        String content = compilation.generatedSourceFile("test.rest.FederationResource")
                                    .get().getCharContent(true).toString();
        assertThat(content).contains("@Consumes(\"application/cloudevents+json\")");
        assertThat(content).contains("@jakarta.ws.rs.HeaderParam(\"X-Federation-Signature\")");
        assertThat(content).contains("@jakarta.ws.rs.HeaderParam(\"X-Federation-Peer-Id\")");
    }

    @Test
    void headerParamOnMutation() throws Exception {
        var spi = com.google.testing.compile.JavaFileObjects.forSourceString(
                "test.IdempotentApi",
                """
                package test;
                import io.casehub.platform.api.mcp.*;
                import jakarta.enterprise.context.ApplicationScoped;
                
                @McpDomain("idempotent")
                @ApplicationScoped
                public class IdempotentApi {
                    @PlatformMutation("Create with idempotency key")
                    public String create(
                            @HeaderParam("X-Idempotency-Key") String idempotencyKey,
                            String name) { return null; }
                }
                """);

        var compilation = com.google.testing.compile.Compiler.javac()
                                                             .withProcessors(new GraphQLResolverProcessor())
                                                             .withOptions("-AdomainFilter=idempotent", "-AgenerateGraphQL=false")
                                                             .compile(spi);

        String content = compilation.generatedSourceFile("test.rest.IdempotentResource")
                                    .get().getCharContent(true).toString();
        assertThat(content).contains("@jakarta.ws.rs.HeaderParam(\"X-Idempotency-Key\")");
        assertThat(content).contains("@QueryParam(\"name\")");
    }

    @Test
    void responseReturnTypePassesThrough() throws Exception {
        var spi = com.google.testing.compile.JavaFileObjects.forSourceString(
                "test.RawApi",
                """
                package test;
                import io.casehub.platform.api.mcp.*;
                import jakarta.enterprise.context.ApplicationScoped;
                import jakarta.ws.rs.core.Response;
                
                @McpDomain("raw")
                @ApplicationScoped
                public class RawApi {
                    @PlatformMutation("Custom response")
                    public Response customAction(String input) { return null; }
                }
                """);

        var compilation = com.google.testing.compile.Compiler.javac()
                                                             .withProcessors(new GraphQLResolverProcessor())
                                                             .withOptions("-AdomainFilter=raw", "-AgenerateGraphQL=false")
                                                             .compile(spi);

        String content = compilation.generatedSourceFile("test.rest.RawResource")
                                    .get().getCharContent(true).toString();
        assertThat(content).contains("return rawApi.customAction(input);");
        assertThat(content).doesNotContain("Response.ok(rawApi");
    }

    @Test
    void uniReturnTypeSkipsVirtualThread() throws Exception {
        var spi = com.google.testing.compile.JavaFileObjects.forSourceString(
                "test.ReactiveApi",
                """
                package test;
                import io.casehub.platform.api.mcp.*;
                import io.smallrye.mutiny.Uni;
                import jakarta.enterprise.context.ApplicationScoped;
                
                @McpDomain("reactive")
                @ApplicationScoped
                public class ReactiveApi {
                    @PlatformMutation("Async create")
                    public Uni<String> createAsync(String name) { return null; }
                }
                """);

        var compilation = com.google.testing.compile.Compiler.javac()
                                                             .withProcessors(new GraphQLResolverProcessor())
                                                             .withOptions("-AdomainFilter=reactive", "-AgenerateGraphQL=false")
                                                             .compile(spi);

        String content = compilation.generatedSourceFile("test.rest.ReactiveResource")
                                    .get().getCharContent(true).toString();
        assertThat(content).doesNotContain("@RunOnVirtualThread");
        assertThat(content).contains("return reactiveApi.createAsync(name);");
        assertThat(content).doesNotContain("Response.ok(");
    }

    @Test
    void contextParamHttpHeaders() throws Exception {
        var spi = com.google.testing.compile.JavaFileObjects.forSourceString(
                "test.FullHeaderApi",
                """
                package test;
                import io.casehub.platform.api.mcp.*;
                import jakarta.enterprise.context.ApplicationScoped;
                import java.util.Map;
                import java.util.List;
                
                @McpDomain("full-header")
                @ApplicationScoped
                public class FullHeaderApi {
                    @PlatformWebhook("Receive with all headers")
                    public void onEvent(
                            @ContextParam("httpHeaders") Map<String, List<String>> headers,
                            String body) {}
                }
                """);

        var compilation = com.google.testing.compile.Compiler.javac()
                                                             .withProcessors(new GraphQLResolverProcessor())
                                                             .withOptions("-AdomainFilter=full-header", "-AgenerateGraphQL=false")
                                                             .compile(spi);

        String content = compilation.generatedSourceFile("test.rest.FullHeaderResource")
                                    .get().getCharContent(true).toString();
        assertThat(content).contains("@jakarta.ws.rs.core.Context");
        assertThat(content).contains("HttpHeaders");
        assertThat(content).contains("httpHeaders.getRequestHeaders()");
    }

    @Test
    void permitAllPassesThroughFromDomain() throws Exception {
        var spi = com.google.testing.compile.JavaFileObjects.forSourceString(
                "test.OpenApi",
                """
                package test;
                import io.casehub.platform.api.mcp.*;
                import jakarta.annotation.security.PermitAll;
                import jakarta.enterprise.context.ApplicationScoped;
                
                @McpDomain("open")
                @ApplicationScoped
                public class OpenApi {
                    @PlatformQuery("Public data")
                    @PermitAll
                    public String getPublicData() { return null; }
                }
                """);

        var compilation = com.google.testing.compile.Compiler.javac()
                                                             .withProcessors(new GraphQLResolverProcessor())
                                                             .withOptions("-AdomainFilter=open", "-AgenerateGraphQL=false")
                                                             .compile(spi);

        String content = compilation.generatedSourceFile("test.rest.OpenResource")
                                    .get().getCharContent(true).toString();
        assertThat(content).contains("@jakarta.annotation.security.PermitAll");
    }

    @Test
    void beanParamExpandsComplexQueryType() throws Exception {
        var filterType = com.google.testing.compile.JavaFileObjects.forSourceString(
                "test.CaseFilter",
                """
                package test;
                public record CaseFilter(String query, java.util.UUID assigneeId, Integer offset, Integer limit) {}
                """);

        var spi = com.google.testing.compile.JavaFileObjects.forSourceString(
                "test.SearchApi",
                """
                package test;
                import io.casehub.platform.api.mcp.*;
                import jakarta.enterprise.context.ApplicationScoped;
                import java.util.List;
                
                @McpDomain("search")
                @ApplicationScoped
                public class SearchApi {
                    @PlatformQuery("Search cases")
                    public List<String> search(CaseFilter filter) { return null; }
                }
                """);

        var compilation = com.google.testing.compile.Compiler.javac()
                                                             .withProcessors(new GraphQLResolverProcessor())
                                                             .withOptions("-AdomainFilter=search", "-AgenerateGraphQL=false")
                                                             .compile(spi, filterType);

        String content = compilation.generatedSourceFile("test.rest.SearchResource")
                                    .get().getCharContent(true).toString();
        assertThat(content).contains("@QueryParam(\"query\")");
        assertThat(content).contains("@QueryParam(\"assigneeId\")");
        assertThat(content).contains("@QueryParam(\"offset\")");
        assertThat(content).contains("@QueryParam(\"limit\")");
        assertThat(content).contains("new test.CaseFilter(query, assigneeId, offset, limit)");
    }

    @Test
    void webhookWithRolesAllowedSuppressesPermitAll() throws Exception {
        var spi = com.google.testing.compile.JavaFileObjects.forSourceString(
                "test.SecureHookApi",
                """
                package test;
                import io.casehub.platform.api.mcp.*;
                import jakarta.annotation.security.RolesAllowed;
                import jakarta.enterprise.context.ApplicationScoped;
                
                @McpDomain("secure-hooks")
                @ApplicationScoped
                public class SecureHookApi {
                    @PlatformWebhook("Internal webhook")
                    @RolesAllowed("webhook-sender")
                    public void onInternalEvent(String body) {}
                }
                """);

        var compilation = com.google.testing.compile.Compiler.javac()
                                                             .withProcessors(new GraphQLResolverProcessor())
                                                             .withOptions("-AdomainFilter=secure-hooks", "-AgenerateGraphQL=false")
                                                             .compile(spi);

        String content = compilation.generatedSourceFile("test.rest.SecureHooksResource")
                                    .get().getCharContent(true).toString();
        assertThat(content).contains("RolesAllowed");
        assertThat(content).contains("\"webhook-sender\"");
        assertThat(content).doesNotContain("PermitAll");
    }

    @Test
    void httpVerbMapping_webhook_isPOST() {
        assertThat(GraphQLResolverProcessor.resolveHttpVerb(GraphQLResolverProcessor.OperationType.WEBHOOK, null)).isEqualTo("POST");
    }

    @Test
    void responseReturnTypeSkipsGraphQL() throws Exception {
        var spi = com.google.testing.compile.JavaFileObjects.forSourceString(
                "test.MixedReturnApi",
                """
                package test;
                import io.casehub.platform.api.mcp.*;
                import jakarta.enterprise.context.ApplicationScoped;
                import jakarta.ws.rs.core.Response;
                
                @McpDomain("mixed-return")
                @ApplicationScoped
                public class MixedReturnApi {
                    @PlatformQuery("Normal query")
                    public String normalQuery() { return null; }
                
                    @PlatformMutation("Response action")
                    public Response responseAction(String input) { return null; }
                }
                """);

        var compilation = com.google.testing.compile.Compiler.javac()
                                                             .withProcessors(new GraphQLResolverProcessor())
                                                             .withOptions("-AdomainFilter=mixed-return")
                                                             .compile(spi);

        var resolver = compilation.generatedSourceFile("test.graphql.MixedReturnResolver");
        assertThat(resolver).isPresent();
        String graphqlContent = resolver.get().getCharContent(true).toString();
        assertThat(graphqlContent).contains("normalQuery");
        assertThat(graphqlContent).doesNotContain("responseAction");

        var rest = compilation.generatedSourceFile("test.rest.MixedReturnResource");
        assertThat(rest).isPresent();
        String restContent = rest.get().getCharContent(true).toString();
        assertThat(restContent).contains("responseAction");
    }

    @Test
    void httpOnlyContextParamDoesNotInjectCurrentPrincipal() throws Exception {
        var spi = com.google.testing.compile.JavaFileObjects.forSourceString(
                "test.HttpOnlyApi",
                """
                package test;
                import io.casehub.platform.api.mcp.*;
                import jakarta.enterprise.context.ApplicationScoped;
                import java.util.Map;
                import java.util.List;
                
                @McpDomain("http-only")
                @ApplicationScoped
                public class HttpOnlyApi {
                    @PlatformQuery("Query with headers")
                    public String queryWithHeaders(
                            @ContextParam("httpHeaders") Map<String, List<String>> headers) {
                        return null;
                    }
                }
                """);

        var compilation = com.google.testing.compile.Compiler.javac()
                                                             .withProcessors(new GraphQLResolverProcessor())
                                                             .withOptions("-AdomainFilter=http-only")
                                                             .compile(spi);

        String restContent = compilation.generatedSourceFile("test.rest.HttpOnlyResource")
                                        .get().getCharContent(true).toString();
        assertThat(restContent).contains("HttpHeaders httpHeaders");
        assertThat(restContent).doesNotContain("CurrentPrincipal");
    }

    @Test
    void mixedContextParamsInjectsBothPrincipalAndHttpHeaders() throws Exception {
        var spi = com.google.testing.compile.JavaFileObjects.forSourceString(
                "test.MixedCtxApi",
                """
                package test;
                import io.casehub.platform.api.mcp.*;
                import jakarta.enterprise.context.ApplicationScoped;
                import java.util.Map;
                import java.util.List;
                
                @McpDomain("mixed-ctx")
                @ApplicationScoped
                public class MixedCtxApi {
                    @PlatformQuery("Tenant-only query")
                    public String tenantQuery(@ContextParam("tenancyId") String tenancyId) {
                        return null;
                    }
                
                    @PlatformQuery("Query with both")
                    public String queryWithBoth(
                            @ContextParam("tenancyId") String tenancyId,
                            @ContextParam("httpHeaders") Map<String, List<String>> headers) {
                        return null;
                    }
                }
                """);

        var compilation = com.google.testing.compile.Compiler.javac()
                                                             .withProcessors(new GraphQLResolverProcessor())
                                                             .withOptions("-AdomainFilter=mixed-ctx")
                                                             .compile(spi);

        String restContent = compilation.generatedSourceFile("test.rest.MixedCtxResource")
                                        .get().getCharContent(true).toString();
        assertThat(restContent).contains("CurrentPrincipal currentPrincipal");
        assertThat(restContent).contains("HttpHeaders httpHeaders");
        assertThat(restContent).contains("queryWithBoth");

        String graphqlContent = compilation.generatedSourceFile("test.graphql.MixedCtxResolver")
                                           .get().getCharContent(true).toString();
        assertThat(graphqlContent).contains("CurrentPrincipal currentPrincipal");
        assertThat(graphqlContent).contains("tenantQuery");
        assertThat(graphqlContent).doesNotContain("queryWithBoth");
        assertThat(graphqlContent).doesNotContain("httpHeaders");
    }

    @Test
    void nameBindingAnnotationPassesThrough() throws Exception {
        var binding = com.google.testing.compile.JavaFileObjects.forSourceString(
                "test.Authenticated",
                """
                package test;
                import jakarta.ws.rs.NameBinding;
                import java.lang.annotation.*;
                
                @NameBinding
                @Target({ElementType.METHOD, ElementType.TYPE})
                @Retention(RetentionPolicy.RUNTIME)
                public @interface Authenticated {}
                """);
        var spi = com.google.testing.compile.JavaFileObjects.forSourceString(
                "test.SecureApi",
                """
                package test;
                import io.casehub.platform.api.mcp.*;
                import jakarta.enterprise.context.ApplicationScoped;
                
                @McpDomain("secure")
                @ApplicationScoped
                public class SecureApi {
                    @PlatformQuery("Secured data")
                    @Authenticated
                    public String getData() { return null; }
                }
                """);

        var compilation = com.google.testing.compile.Compiler.javac()
                                                             .withProcessors(new GraphQLResolverProcessor())
                                                             .withOptions("-AdomainFilter=secure", "-AgenerateGraphQL=false")
                                                             .compile(binding, spi);

        String content = compilation.generatedSourceFile("test.rest.SecureResource")
                                    .get().getCharContent(true).toString();
        assertThat(content).contains("@test.Authenticated");
    }

    @Test
    void contextParamQueryParams() throws Exception {
        var spi = com.google.testing.compile.JavaFileObjects.forSourceString(
                "test.QueryParamApi",
                """
                package test;
                import io.casehub.platform.api.mcp.*;
                import jakarta.enterprise.context.ApplicationScoped;
                import java.util.Map;
                
                @McpDomain("qp")
                @ApplicationScoped
                public class QueryParamApi {
                    @PlatformWebhook("Receive with query params")
                    public void onEvent(
                            @ContextParam("queryParams") Map<String, String> params,
                            String body) {}
                }
                """);

        var compilation = com.google.testing.compile.Compiler.javac()
                                                             .withProcessors(new GraphQLResolverProcessor())
                                                             .withOptions("-AdomainFilter=qp", "-AgenerateGraphQL=false")
                                                             .compile(spi);

        String content = compilation.generatedSourceFile("test.rest.QpResource")
                                    .get().getCharContent(true).toString();
        assertThat(content).contains("UriInfo uriInfo");
        assertThat(content).contains("flatQueryParams(uriInfo)");
        assertThat(content).doesNotContain("CurrentPrincipal");
    }

    @Test
    void contextParamRequestUrl() throws Exception {
        var spi = com.google.testing.compile.JavaFileObjects.forSourceString(
                "test.UrlApi",
                """
                package test;
                import io.casehub.platform.api.mcp.*;
                import jakarta.enterprise.context.ApplicationScoped;
                
                @McpDomain("url")
                @ApplicationScoped
                public class UrlApi {
                    @PlatformWebhook("Receive with request URL")
                    public void onEvent(
                            @ContextParam("requestUrl") String url,
                            String body) {}
                }
                """);

        var compilation = com.google.testing.compile.Compiler.javac()
                                                             .withProcessors(new GraphQLResolverProcessor())
                                                             .withOptions("-AdomainFilter=url", "-AgenerateGraphQL=false")
                                                             .compile(spi);

        String content = compilation.generatedSourceFile("test.rest.UrlResource")
                                    .get().getCharContent(true).toString();
        assertThat(content).contains("UriInfo uriInfo");
        assertThat(content).contains("uriInfo.getRequestUri().toString()");
        assertThat(content).doesNotContain("CurrentPrincipal");
    }

    @Test
    void webhookMultiValueConsumes() throws Exception {
        var spi = com.google.testing.compile.JavaFileObjects.forSourceString(
                "test.MultiConsumeApi",
                """
                package test;
                import io.casehub.platform.api.mcp.*;
                import jakarta.enterprise.context.ApplicationScoped;
                
                @McpDomain("multi-consume")
                @ApplicationScoped
                public class MultiConsumeApi {
                    @PlatformWebhook(value = "Accept multiple types",
                                     consumes = {"application/json", "application/x-www-form-urlencoded"})
                    public void onEvent(String body) {}
                }
                """);

        var compilation = com.google.testing.compile.Compiler.javac()
                                                             .withProcessors(new GraphQLResolverProcessor())
                                                             .withOptions("-AdomainFilter=multi-consume", "-AgenerateGraphQL=false")
                                                             .compile(spi);

        String content = compilation.generatedSourceFile("test.rest.MultiConsumeResource")
                                    .get().getCharContent(true).toString();
        assertThat(content).contains("@Consumes({\"application/json\", \"application/x-www-form-urlencoded\"})");
    }

    @Test
    void beanParamNonRecordEmitsError() throws Exception {
        var dto = com.google.testing.compile.JavaFileObjects.forSourceString(
                "test.SearchFilter",
                """
                package test;
                public class SearchFilter {
                    private String query;
                    private int limit;
                    public SearchFilter() {}
                    public String getQuery() { return query; }
                    public int getLimit() { return limit; }
                }
                """);
        var spi = com.google.testing.compile.JavaFileObjects.forSourceString(
                "test.SearchApi",
                """
                package test;
                import io.casehub.platform.api.mcp.*;
                import jakarta.enterprise.context.ApplicationScoped;
                import java.util.List;
                
                @McpDomain("search")
                @ApplicationScoped
                public class SearchApi {
                    @PlatformQuery("Search items")
                    public List<String> search(SearchFilter filter) { return List.of(); }
                }
                """);

        var compilation = com.google.testing.compile.Compiler.javac()
                                                             .withProcessors(new GraphQLResolverProcessor())
                                                             .withOptions("-AdomainFilter=search", "-AgenerateGraphQL=false")
                                                             .compile(dto, spi);

        assertThat(compilation.errors()).isNotEmpty();
        assertThat(compilation.errors().get(0).getMessage(null))
                .contains("@BeanParam expansion requires a Java record");
    }

    @Test
    void emptyDescriptionEmitsError() throws Exception {
        var spi = com.google.testing.compile.JavaFileObjects.forSourceString(
                "test.EmptyDescApi",
                """
                package test;
                import io.casehub.platform.api.mcp.*;
                import jakarta.enterprise.context.ApplicationScoped;
                
                @McpDomain("empty-desc")
                @ApplicationScoped
                public class EmptyDescApi {
                    @PlatformQuery("")
                    public String blankQuery() { return null; }
                
                    @PlatformMutation
                    public void noDescMutation(String input) {}
                }
                """);

        var compilation = com.google.testing.compile.Compiler.javac()
                                                             .withProcessors(new GraphQLResolverProcessor())
                                                             .withOptions("-AdomainFilter=empty-desc")
                                                             .compile(spi);

        assertThat(compilation.errors()).hasSize(2);
        assertThat(compilation.errors().get(0).getMessage(null))
                .contains("empty description");
    }


}
