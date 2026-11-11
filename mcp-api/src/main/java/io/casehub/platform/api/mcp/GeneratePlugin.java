package io.casehub.platform.api.mcp;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface GeneratePlugin {

    String name() default "";

    String description() default "";

    boolean absorb() default false;

    String subject() default "";

    Class<?> capability() default void.class;

    Class<?> spi() default void.class;

    String accessor() default "";

    String spiMethod() default "";

    String[] optionalParams() default {};

    boolean exclude() default false;
}
