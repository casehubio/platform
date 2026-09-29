package io.casehub.platform.expression.spring;

import io.casehub.platform.api.expression.ConfigManager;
import io.casehub.platform.api.expression.ExpressionEngine;
import io.casehub.platform.api.expression.SecretManager;
import io.casehub.platform.expression.ConfigManagerCore;
import io.casehub.platform.expression.DefaultExpressionEngineRegistry;
import io.casehub.platform.expression.JQEvaluatorCore;
import io.casehub.platform.expression.JQExpressionEngine;
import io.casehub.platform.expression.JexlExpressionEngine;
import io.casehub.platform.expression.MvelExpressionEngine;
import io.casehub.platform.expression.SecretManagerCore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import java.util.List;

@AutoConfiguration
@ConditionalOnClass(ConfigManagerCore.class)
public class ExpressionSpringAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ConfigManager.class)
    public ConfigManagerCore configManager(Environment environment) {
        return new ConfigManagerCore(new EnvironmentPropertySource(environment));
    }

    @Bean
    @ConditionalOnMissingBean(SecretManager.class)
    public SecretManagerCore secretManager(Environment environment) {
        return new SecretManagerCore(new EnvironmentPropertySource(environment));
    }

    @Bean
    @ConditionalOnMissingBean(JQEvaluatorCore.class)
    public JQEvaluatorCore jqEvaluator(SecretManager secretManager, ConfigManager configManager) {
        return new JQEvaluatorCore(secretManager, configManager);
    }

    @Bean
    @ConditionalOnMissingBean
    public DefaultExpressionEngineRegistry defaultExpressionEngineRegistry(
            List<ExpressionEngine> engines) {
        return new DefaultExpressionEngineRegistry(engines);
    }

    @Bean
    @ConditionalOnMissingBean
    public MvelExpressionEngine mvelExpressionEngine() {
        return new MvelExpressionEngine();
    }

    @Bean
    @ConditionalOnMissingBean
    public JQExpressionEngine jqExpressionEngine() {
        return new JQExpressionEngine();
    }

    @Bean
    @ConditionalOnMissingBean
    public JexlExpressionEngine jexlExpressionEngine() {
        return new JexlExpressionEngine();
    }
}
