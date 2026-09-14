package io.casehub.platform.rest.spring.generator;

import com.palantir.javapoet.AnnotationSpec;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.ParameterSpec;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeSpec;

import javax.lang.model.element.Modifier;

public class ProviderWriter {

    private static final ClassName CONTROLLER_ADVICE = ClassName.get("org.springframework.web.bind.annotation", "ControllerAdvice");
    private static final ClassName EXCEPTION_HANDLER = ClassName.get("org.springframework.web.bind.annotation", "ExceptionHandler");
    private static final ClassName RESPONSE_ENTITY = ClassName.get("org.springframework.http", "ResponseEntity");
    private static final ClassName COMPONENT = ClassName.get("org.springframework.stereotype", "Component");
    private static final ClassName ORDER = ClassName.get("org.springframework.core.annotation", "Order");
    private static final ClassName FILTER = ClassName.get("jakarta.servlet", "Filter");
    private static final ClassName SERVLET_REQUEST = ClassName.get("jakarta.servlet", "ServletRequest");
    private static final ClassName SERVLET_RESPONSE = ClassName.get("jakarta.servlet", "ServletResponse");
    private static final ClassName FILTER_CHAIN = ClassName.get("jakarta.servlet", "FilterChain");
    private static final ClassName HANDLER_INTERCEPTOR = ClassName.get("org.springframework.web.servlet", "HandlerInterceptor");
    private static final ClassName HTTP_SERVLET_REQUEST = ClassName.get("jakarta.servlet.http", "HttpServletRequest");
    private static final ClassName HTTP_SERVLET_RESPONSE = ClassName.get("jakarta.servlet.http", "HttpServletResponse");

    public JavaFile generate(ProviderDescriptor descriptor, String targetPackage) {
        TypeSpec typeSpec = switch (descriptor.providerKind()) {
            case EXCEPTION_MAPPER -> generateExceptionHandler(descriptor);
            case REQUEST_FILTER -> descriptor.isSecurityFilter()
                    ? generateFilter(descriptor)
                    : generateInterceptor(descriptor);
            case RESPONSE_FILTER -> generateResponseAdvice(descriptor);
            case PARAM_CONVERTER -> generateConverter(descriptor);
        };

        return JavaFile.builder(targetPackage, typeSpec).build();
    }

    private TypeSpec generateExceptionHandler(ProviderDescriptor descriptor) {
        String simpleName = simpleClassName(descriptor.className());
        String adviceName = simpleName.replace("Mapper", "Advice");
        if (adviceName.equals(simpleName)) {
            adviceName = simpleName + "Advice";
        }

        ClassName exceptionType = ClassName.bestGuess(descriptor.targetType());

        MethodSpec handleMethod = MethodSpec.methodBuilder("handle")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(AnnotationSpec.builder(EXCEPTION_HANDLER)
                        .addMember("value", "$T.class", exceptionType)
                        .build())
                .addParameter(exceptionType, "ex")
                .returns(ParameterizedTypeName.get(RESPONSE_ENTITY, ClassName.get("java.lang", "Object")))
                .addStatement("return $T.badRequest().body(ex.getMessage())", RESPONSE_ENTITY)
                .build();

        return TypeSpec.classBuilder(adviceName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(CONTROLLER_ADVICE)
                .addMethod(handleMethod)
                .build();
    }

    private TypeSpec generateFilter(ProviderDescriptor descriptor) {
        String simpleName = simpleClassName(descriptor.className());
        String springName = "Spring" + simpleName;

        MethodSpec doFilter = MethodSpec.methodBuilder("doFilter")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .addParameter(SERVLET_REQUEST, "request")
                .addParameter(SERVLET_RESPONSE, "response")
                .addParameter(FILTER_CHAIN, "chain")
                .addException(ClassName.get("java.io", "IOException"))
                .addException(ClassName.get("jakarta.servlet", "ServletException"))
                .addStatement("chain.doFilter(request, response)")
                .build();

        return TypeSpec.classBuilder(springName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(COMPONENT)
                .addAnnotation(AnnotationSpec.builder(ORDER)
                        .addMember("value", "$L", descriptor.priority())
                        .build())
                .addSuperinterface(FILTER)
                .addMethod(doFilter)
                .build();
    }

    private TypeSpec generateInterceptor(ProviderDescriptor descriptor) {
        String simpleName = simpleClassName(descriptor.className());
        String springName = "Spring" + simpleName;

        MethodSpec preHandle = MethodSpec.methodBuilder("preHandle")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .addParameter(HTTP_SERVLET_REQUEST, "request")
                .addParameter(HTTP_SERVLET_RESPONSE, "response")
                .addParameter(ClassName.get("java.lang", "Object"), "handler")
                .returns(boolean.class)
                .addStatement("return true")
                .build();

        return TypeSpec.classBuilder(springName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(COMPONENT)
                .addSuperinterface(HANDLER_INTERCEPTOR)
                .addMethod(preHandle)
                .build();
    }

    private TypeSpec generateResponseAdvice(ProviderDescriptor descriptor) {
        String simpleName = simpleClassName(descriptor.className());
        return TypeSpec.classBuilder("Spring" + simpleName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(CONTROLLER_ADVICE)
                .build();
    }

    private TypeSpec generateConverter(ProviderDescriptor descriptor) {
        String simpleName = simpleClassName(descriptor.className());
        return TypeSpec.classBuilder("Spring" + simpleName)
                .addModifiers(Modifier.PUBLIC)
                .build();
    }

    private String simpleClassName(String fqn) {
        int dot = fqn.lastIndexOf('.');
        return dot >= 0 ? fqn.substring(dot + 1) : fqn;
    }
}
