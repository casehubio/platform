package io.casehub.platform.scim.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.platform.api.identity.GroupMember;
import io.casehub.platform.api.identity.GroupMembershipProvider;
import io.casehub.platform.scim.ScimClient;
import io.casehub.platform.scim.ScimGroupMembershipProviderCore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

import java.util.Set;

@AutoConfiguration
@ConditionalOnProperty(name = "casehub.platform.scim.url")
public class ScimSpringManualConfig {

    @Bean
    @ConditionalOnMissingBean(ScimClient.class)
    public RestClientScimClient restClientScimClient(
            @Value("${casehub.platform.scim.url}") String scimUrl,
            @Value("${casehub.platform.scim.token:}") String token,
            ObjectMapper objectMapper) {
        var builder = RestClient.builder().baseUrl(scimUrl);
        if (!token.isBlank()) {
            builder.defaultHeader("Authorization", "Bearer " + token);
        }
        return new RestClientScimClient(builder.build(), objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean(GroupMembershipProvider.class)
    public GroupMembershipProvider cachingScimGroupMembershipProvider(
            ScimGroupMembershipProviderCore core) {
        return new CachingScimGroupMembershipProvider(core);
    }

    static class CachingScimGroupMembershipProvider implements GroupMembershipProvider {
        private final ScimGroupMembershipProviderCore delegate;

        CachingScimGroupMembershipProvider(ScimGroupMembershipProviderCore delegate) {
            this.delegate = delegate;
        }

        @Override
        @Cacheable("scim-group-members")
        public Set<GroupMember> membersOf(String groupName, String tenancyId) {
            return delegate.membersOf(groupName, tenancyId);
        }
    }
}
