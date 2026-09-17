package io.casehub.platform.acl.spring.jpa;

import io.casehub.platform.api.acl.AccessControlProvider;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.identity.GroupMembershipProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@AutoConfiguration
@ConditionalOnClass(SpringAccessControlProvider.class)
@EnableJpaRepositories(basePackageClasses = AclEntryEntityRepository.class)
public class AclJpaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(AccessControlProvider.class)
    public SpringAccessControlProvider springAccessControlProvider(
            AclEntryEntityRepository entryRepo,
            AclAuditLogEntityRepository auditRepo,
            ResourceParentEntityRepository parentRepo,
            GroupMembershipProvider groupMembership,
            CurrentPrincipal principal) {
        return new SpringAccessControlProvider(entryRepo, auditRepo, parentRepo, groupMembership, principal);
    }
}
