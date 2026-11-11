package io.casehub.platform.graphql.generator;

import org.junit.jupiter.api.Test;

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


}
