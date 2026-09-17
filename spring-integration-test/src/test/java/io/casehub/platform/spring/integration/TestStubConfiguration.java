package io.casehub.platform.spring.integration;

import io.casehub.platform.mock.MockGroupMembershipProvider;
import io.casehub.platform.mock.NoOpMcpResourceRegistry;
import io.casehub.platform.mock.NoOpPreferenceSchemaRegistry;
import io.casehub.platform.mock.NoOpPreferenceStore;
import io.casehub.platform.mock.NoOpSessionIsolator;
import io.casehub.platform.governance.DefaultPolicyEnforcer;
import io.casehub.platform.governance.PolicyEnforcer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import io.casehub.platform.api.identity.GroupMembershipProvider;
import io.casehub.platform.api.preferences.PreferenceStore;
import io.casehub.platform.api.preferences.PreferenceSchemaRegistry;
import io.casehub.platform.api.mcp.McpResourceRegistry;

/**
 * Test stubs for platform SPIs that don't yet have Spring auto-configuration
 * defaults. Remove individual beans as #504 (agent spring auto-configs) and
 * subsequent issues provide real @ConditionalOnMissingBean defaults.
 */
@TestConfiguration
public class TestStubConfiguration {

    @Bean
    @ConditionalOnMissingBean(GroupMembershipProvider.class)
    public MockGroupMembershipProvider groupMembershipProvider() {
        return new MockGroupMembershipProvider();
    }

    @Bean
    @ConditionalOnMissingBean(PreferenceSchemaRegistry.class)
    public NoOpPreferenceSchemaRegistry preferenceSchemaRegistry() {
        return new NoOpPreferenceSchemaRegistry();
    }

    @Bean
    @ConditionalOnMissingBean(McpResourceRegistry.class)
    public NoOpMcpResourceRegistry mcpResourceRegistry() {
        return new NoOpMcpResourceRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    public NoOpSessionIsolator sessionIsolator() {
        return new NoOpSessionIsolator();
    }

    @Bean
    @ConditionalOnMissingBean(PolicyEnforcer.class)
    public DefaultPolicyEnforcer policyEnforcer() {
        return new DefaultPolicyEnforcer();
    }

    @Bean
    @ConditionalOnMissingBean(com.fasterxml.jackson.databind.ObjectMapper.class)
    public com.fasterxml.jackson.databind.ObjectMapper objectMapper() {
        return new com.fasterxml.jackson.databind.ObjectMapper();
    }
}
