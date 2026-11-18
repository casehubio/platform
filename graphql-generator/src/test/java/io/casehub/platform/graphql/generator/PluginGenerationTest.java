package io.casehub.platform.graphql.generator;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PluginGenerationTest {

    @Test
    void generatePlugins_is_supported_option() {
        var processor = new GraphQLResolverProcessor();
        assertThat(processor.getSupportedOptions()).contains("generatePlugins", "pluginPackage");
    }

    @Test
    void plugin_name_derivation_interface_domain() {
        assertThat(GraphQLResolverProcessor.derivePluginName("acl", "canAccess"))
                .isEqualTo("acl-can-access");
    }

    @Test
    void plugin_name_derivation_connector_domain() {
        assertThat(GraphQLResolverProcessor.derivePluginName("connectors/bank", "listTransactions"))
                .isEqualTo("bank-list-transactions");
    }

    @Test
    void plugin_name_derivation_single_word_method() {
        assertThat(GraphQLResolverProcessor.derivePluginName("connectors/bank", "balance"))
                .isEqualTo("bank-balance");
    }

    @Test
    void plugin_name_derivation_nested_domain() {
        assertThat(GraphQLResolverProcessor.derivePluginName("connectors/voice", "speak"))
                .isEqualTo("voice-speak");
    }

    @Test
    void plugin_name_derivation_simple_domain() {
        assertThat(GraphQLResolverProcessor.derivePluginName("labels", "children"))
                .isEqualTo("labels-children");
    }

    @Test
    void plugin_name_derivation_consecutive_uppercase() {
        assertThat(GraphQLResolverProcessor.derivePluginName("agent-http", "listHTTPMethods"))
                .isEqualTo("agent-http-list-http-methods");
    }

    @Test
    void formatDefaultLiteral_integer_unquoted() {
        assertThat(GraphQLResolverProcessor.formatDefaultLiteral("Integer", "20")).isEqualTo("20");
    }

    @Test
    void formatDefaultLiteral_int_primitive_unquoted() {
        assertThat(GraphQLResolverProcessor.formatDefaultLiteral("int", "20")).isEqualTo("20");
    }

    @Test
    void formatDefaultLiteral_long_with_suffix() {
        assertThat(GraphQLResolverProcessor.formatDefaultLiteral("Long", "100")).isEqualTo("100L");
    }

    @Test
    void formatDefaultLiteral_long_primitive_with_suffix() {
        assertThat(GraphQLResolverProcessor.formatDefaultLiteral("long", "100")).isEqualTo("100L");
    }

    @Test
    void formatDefaultLiteral_double_unquoted() {
        assertThat(GraphQLResolverProcessor.formatDefaultLiteral("Double", "3.14")).isEqualTo("3.14");
    }

    @Test
    void formatDefaultLiteral_double_whole_number_gets_dot() {
        assertThat(GraphQLResolverProcessor.formatDefaultLiteral("Double", "20")).isEqualTo("20.0");
    }

    @Test
    void formatDefaultLiteral_float_with_suffix() {
        assertThat(GraphQLResolverProcessor.formatDefaultLiteral("Float", "1.5")).isEqualTo("1.5f");
    }

    @Test
    void formatDefaultLiteral_boolean_unquoted() {
        assertThat(GraphQLResolverProcessor.formatDefaultLiteral("Boolean", "true")).isEqualTo("true");
    }

    @Test
    void formatDefaultLiteral_string_quoted() {
        assertThat(GraphQLResolverProcessor.formatDefaultLiteral("String", "json")).isEqualTo("\"json\"");
    }

    @Test
    void formatDefaultLiteral_unknown_type_quoted() {
        assertThat(GraphQLResolverProcessor.formatDefaultLiteral("SomeEnum", "VALUE")).isEqualTo("\"VALUE\"");
    }

    @Test
    void isPaginationParam_cursor() {
        assertThat(GraphQLResolverProcessor.isPaginationParam("cursor")).isTrue();
    }

    @Test
    void isPaginationParam_nextPageToken() {
        assertThat(GraphQLResolverProcessor.isPaginationParam("nextPageToken")).isTrue();
    }

    @Test
    void isPaginationParam_syncToken() {
        assertThat(GraphQLResolverProcessor.isPaginationParam("syncToken")).isTrue();
    }

    @Test
    void isPaginationParam_pageToken() {
        assertThat(GraphQLResolverProcessor.isPaginationParam("pageToken")).isTrue();
    }

    @Test
    void isPaginationParam_regular_param() {
        assertThat(GraphQLResolverProcessor.isPaginationParam("accountId")).isFalse();
    }

    @Test
    void formatDefaultLiteral_boolean_false_is_noop() {
        assertThat(GraphQLResolverProcessor.formatDefaultLiteral("boolean", "false")).isEqualTo("false");
    }

    @Test
    void pluginWithContextParam_injectsServiceRegistryLookup() throws Exception {
        var spi = com.google.testing.compile.JavaFileObjects.forSourceString(
                "test.TenantApi",
                """
                package test;
                import io.casehub.platform.api.mcp.*;
                import java.util.List;

                @McpDomain("tenant-query")
                public interface TenantApi {
                    @PlatformQuery("List tenant items")
                    List<String> listItems(@ContextParam("tenancyId") String tenancyId, String filter);
                }
                """);

        var compilation = com.google.testing.compile.Compiler.javac()
                .withProcessors(new GraphQLResolverProcessor())
                .withOptions("-AdomainFilter=tenant-query", "-AgeneratePlugins=true",
                        "-AgenerateGraphQL=false", "-AgenerateRest=false")
                .compile(spi);

        var pluginSource = compilation.generatedSourceFile(
                "test.plugin.TenantQueryListItemsPlugin");
        assertThat(pluginSource).as("Plugin source should be generated").isPresent();

        String content = pluginSource.get().getCharContent(true).toString();
        assertThat(content).contains("io.casehub.platform.api.identity.CurrentPrincipal __principal");
        assertThat(content).contains("__principal.tenancyId()");
    }

    @Test
    void pluginWithHttpOnlyContextParam_skipsParam() throws Exception {
        var spi = com.google.testing.compile.JavaFileObjects.forSourceString(
                "test.HeaderApi",
                """
                package test;
                import io.casehub.platform.api.mcp.*;
                import java.util.Map;
                import java.util.List;

                @McpDomain("header-query")
                public interface HeaderApi {
                    @PlatformQuery("Query with headers")
                    String query(@ContextParam("httpHeaders") Map<String, List<String>> headers, String filter);
                }
                """);

        var compilation = com.google.testing.compile.Compiler.javac()
                .withProcessors(new GraphQLResolverProcessor())
                .withOptions("-AdomainFilter=header-query", "-AgeneratePlugins=true",
                        "-AgenerateGraphQL=false", "-AgenerateRest=false")
                .compile(spi);

        var pluginSource = compilation.generatedSourceFile(
                "test.plugin.HeaderQueryQueryPlugin");
        assertThat(pluginSource).as("Plugin source should be generated").isPresent();

        String content = pluginSource.get().getCharContent(true).toString();
        assertThat(content).doesNotContain("CurrentPrincipal");
        assertThat(content).contains("null");
    }

    @Test
    void streamingPlugin_withContextParam_includesPrincipal() throws Exception {
        var spi = com.google.testing.compile.JavaFileObjects.forSourceString(
                "test.StreamApi",
                """
                package test;
                import io.casehub.platform.api.mcp.*;
                
                @McpDomain("stream-tenant")
                public interface StreamApi {
                    @PlatformMutation(streaming = true)
                    @GeneratePlugin(subject = "channelId")
                    String streamItems(String channelId, @ContextParam("tenancyId") String tenancyId);
                }
                """);

        var compilation = com.google.testing.compile.Compiler.javac()
                                                             .withProcessors(new GraphQLResolverProcessor())
                                                             .withOptions("-AdomainFilter=stream-tenant", "-AgeneratePlugins=true",
                                                                          "-AgenerateGraphQL=false", "-AgenerateRest=false")
                                                             .compile(spi);

        var pluginSource = compilation.generatedSourceFile(
                "test.plugin.StreamTenantStreamItemsPlugin");
        assertThat(pluginSource).as("Plugin source should be generated").isPresent();

        String content = pluginSource.get().getCharContent(true).toString();
        assertThat(content).contains("io.casehub.platform.api.identity.CurrentPrincipal __principal");
        assertThat(content).contains("__principal.tenancyId()");
        assertThat(content).contains("process(");
    }

    @Test
    void streamingPlugin_withoutSubject_usesCorrectReturnType() throws Exception {
        var spi = com.google.testing.compile.JavaFileObjects.forSourceString(
                "test.StreamNoSubjectApi",
                """
                package test;
                import io.casehub.platform.api.mcp.*;
                
                @McpDomain("stream-nosub")
                public interface StreamNoSubjectApi {
                    @PlatformMutation(streaming = true)
                    @GeneratePlugin
                    String streamItems(@ContextParam("tenancyId") String tenancyId);
                }
                """);

        var compilation = com.google.testing.compile.Compiler.javac()
                                                             .withProcessors(new GraphQLResolverProcessor())
                                                             .withOptions("-AdomainFilter=stream-nosub", "-AgeneratePlugins=true",
                                                                          "-AgenerateGraphQL=false", "-AgenerateRest=false")
                                                             .compile(spi);

        var pluginSource = compilation.generatedSourceFile(
                "test.plugin.StreamNosubStreamItemsPlugin");
        assertThat(pluginSource).as("Plugin source should be generated").isPresent();

        String content = pluginSource.get().getCharContent(true).toString();
        assertThat(content).contains("process(");
        assertThat(content).contains("public String process(");
        assertThat(content).contains("io.casehub.platform.api.identity.CurrentPrincipal __principal");
        assertThat(content).contains("__principal.tenancyId()");
        assertThat(content).doesNotContain("public Result execute(");
        assertThat(content).doesNotContain("Result.of(");
    }


    @Test
    void derivePluginName_no_collision_uses_short_name() {
        Set<String> collisions = Set.of();
        assertThat(GraphQLResolverProcessor.derivePluginName("connectors/voice", "speak", collisions))
                .isEqualTo("voice-speak");
    }

    @Test
    void derivePluginName_collision_uses_full_domain_path() {
        Set<String> collisions = Set.of("voice-speak");
        assertThat(GraphQLResolverProcessor.derivePluginName("connectors/voice", "speak", collisions))
                .isEqualTo("connectors-voice-speak");
    }

    @Test
    void derivePluginName_deep_nested_collision_uses_full_path() {
        Set<String> collisions = Set.of("lifecycle-claim");
        assertThat(GraphQLResolverProcessor.derivePluginName("work/case/lifecycle", "claim", collisions))
                .isEqualTo("work-case-lifecycle-claim");
    }

    @Test
    void derivePluginName_simple_domain_unaffected_by_collision() {
        Set<String> collisions = Set.of("voice-speak");
        assertThat(GraphQLResolverProcessor.derivePluginName("labels", "children", collisions))
                .isEqualTo("labels-children");
    }

}
