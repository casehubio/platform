package io.casehub.platform.agent;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface AgentService {
    String model() default "";
    String descriptor() default "";
    Class<?>[] tools() default {};
    String[] mcpServers() default {};
    int timeout() default 0;
    boolean simulationEligible() default false;
}
