package io.casehub.platform.expression.spring;

import io.casehub.platform.api.expression.ConfigManager;
import io.casehub.platform.api.expression.ExpressionEngineRegistry;
import io.casehub.platform.api.expression.SecretManager;
import io.casehub.platform.expression.ConfigManagerCore;
import io.casehub.platform.expression.JQEvaluatorCore;
import io.casehub.platform.expression.PropertySource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

class ExpressionSpringAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ExpressionSpringAutoConfiguration.class));

    @Test
    void configManager_bean_registered() {
        runner.run(ctx -> assertThat(ctx).hasSingleBean(ConfigManager.class));
    }

    @Test
    void secretManager_bean_registered() {
        runner.run(ctx -> assertThat(ctx).hasSingleBean(SecretManager.class));
    }

    @Test
    void jqEvaluatorCore_bean_registered() {
        runner.run(ctx -> assertThat(ctx).hasSingleBean(JQEvaluatorCore.class));
    }

    @Test
    void expressionEngineRegistry_bean_registered() {
        runner.run(ctx -> assertThat(ctx).hasSingleBean(ExpressionEngineRegistry.class));
    }

    @Test
    void secretManager_reads_from_environment() {
        runner.withPropertyValues("casehub.platform.secrets.test.key=secret-value")
                .run(ctx -> {
                    SecretManager sm = ctx.getBean(SecretManager.class);
                    assertThat(sm.secret("test")).containsEntry("key", "secret-value");
                });
    }

    @Test
    void custom_configManager_overrides_default() {
        runner.withBean("customConfigManager", ConfigManager.class,
                        () -> new ConfigManagerCore(new PropertySource() {
                            public Optional<String> getProperty(String n) {
                                return Optional.of("custom");
                            }
                            public Iterable<String> getPropertyNames() {
                                return List.of();
                            }
                        }))
                .run(ctx -> {
                    ConfigManager cm = ctx.getBean(ConfigManager.class);
                    assertThat(cm.config("any", String.class)).hasValue("custom");
                });
    }
}
