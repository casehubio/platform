package io.casehub.platform.agent.guardrail.spring;

import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.guardrail.InputGuardrail;
import io.casehub.platform.agent.guardrail.OutputGuardrail;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;

import java.util.List;
import java.util.logging.Logger;

public class GuardrailBeanPostProcessor implements BeanPostProcessor, Ordered {

    private static final Logger LOG = Logger.getLogger(
            GuardrailBeanPostProcessor.class.getName());

    private final List<InputGuardrail> inputGuardrails;
    private final List<OutputGuardrail> outputGuardrails;
    private final int maxReprompts;

    public GuardrailBeanPostProcessor(List<InputGuardrail> inputGuardrails,
                                       List<OutputGuardrail> outputGuardrails,
                                       int maxReprompts) {
        this.inputGuardrails = inputGuardrails;
        this.outputGuardrails = outputGuardrails;
        this.maxReprompts = maxReprompts;
    }

    @Override
    public int getOrder() {
        return 1400;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName)
            throws BeansException {
        if (bean instanceof AgentProvider provider
                && !(bean instanceof GuardrailAgentProviderWrapper)) {
            LOG.info("Wrapping AgentProvider '" + beanName + "' with guardrails");
            return new GuardrailAgentProviderWrapper(
                provider, inputGuardrails, outputGuardrails, maxReprompts);
        }
        return bean;
    }
}
