package io.casehub.platform.view.spring.jpa;

import io.casehub.platform.api.view.CrossTenantSubjectViewStore;
import io.casehub.platform.api.view.SubjectViewStore;
import io.casehub.platform.api.view.ViewMembershipTracker;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@AutoConfiguration
@ConditionalOnClass(SpringSubjectViewStore.class)
@EnableJpaRepositories(basePackageClasses = SubjectViewEntityRepository.class)
public class PlatformViewJpaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SubjectViewStore.class)
    public SpringSubjectViewStore springSubjectViewStore(SubjectViewEntityRepository repo) {
        return new SpringSubjectViewStore(repo);
    }

    @Bean
    @ConditionalOnMissingBean(CrossTenantSubjectViewStore.class)
    public SpringCrossTenantSubjectViewStore springCrossTenantSubjectViewStore(SubjectViewEntityRepository repo) {
        return new SpringCrossTenantSubjectViewStore(repo);
    }

    @Bean
    @ConditionalOnMissingBean(ViewMembershipTracker.class)
    public SpringViewMembershipTracker springViewMembershipTracker(ViewMembershipEntityRepository repo) {
        return new SpringViewMembershipTracker(repo);
    }
}
