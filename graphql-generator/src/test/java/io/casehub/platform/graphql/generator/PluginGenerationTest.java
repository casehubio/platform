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
}
