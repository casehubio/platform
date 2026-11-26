package io.casehub.platform.agent.service.spring;

import io.casehub.platform.agent.AgentService;
import io.casehub.platform.agent.service.AgentServiceBeans;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.beans.Introspector;
import java.util.List;
import java.util.logging.Logger;

class AgentServiceBeanRegistrar implements BeanDefinitionRegistryPostProcessor, BeanFactoryAware {

    private static final Logger LOG = Logger.getLogger(
            AgentServiceBeanRegistrar.class.getName());

    private ConfigurableListableBeanFactory beanFactory;

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = (ConfigurableListableBeanFactory) beanFactory;
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        List<String> packages;
        try {
            packages = AutoConfigurationPackages.get(beanFactory);
        } catch (IllegalStateException e) {
            LOG.warning("No auto-configuration packages registered — skipping @AgentService scan");
            return;
        }

        var scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
                return beanDefinition.getMetadata().isInterface();
            }
        };
        scanner.addIncludeFilter(new AnnotationTypeFilter(AgentService.class));

        for (String basePackage : packages) {
            for (BeanDefinition candidate : scanner.findCandidateComponents(basePackage)) {
                String className = candidate.getBeanClassName();
                try {
                    Class<?> serviceInterface = Class.forName(className);
                    var bd = new GenericBeanDefinition();
                    bd.setBeanClass(serviceInterface);
                    bd.setInstanceSupplier(() ->
                            beanFactory.getBean(AgentServiceBeans.class).createProxy(serviceInterface));
                    String beanName = Introspector.decapitalize(serviceInterface.getSimpleName());
                    registry.registerBeanDefinition(beanName, bd);
                    LOG.info("Registered @AgentService proxy bean: " + beanName
                            + " (" + className + ")");
                } catch (ClassNotFoundException e) {
                    throw new IllegalStateException(
                            "Cannot load @AgentService interface: " + className, e);
                }
            }
        }
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        // no-op — all work done in postProcessBeanDefinitionRegistry
    }
}
