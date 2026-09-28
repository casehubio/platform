package io.casehub.platform.rest.spring.generator;

import io.casehub.platform.generator.JandexTypeConverter;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.FieldInfo;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.MethodParameterInfo;
import org.jboss.jandex.PrimitiveType;

import java.util.ArrayList;
import java.util.List;

public class RestResourceScanner {

    private static final DotName PATH = DotName.createSimple("jakarta.ws.rs.Path");
    private static final DotName GET = DotName.createSimple("jakarta.ws.rs.GET");
    private static final DotName POST = DotName.createSimple("jakarta.ws.rs.POST");
    private static final DotName PUT = DotName.createSimple("jakarta.ws.rs.PUT");
    private static final DotName DELETE = DotName.createSimple("jakarta.ws.rs.DELETE");
    private static final DotName PATCH = DotName.createSimple("jakarta.ws.rs.PATCH");
    private static final DotName PATH_PARAM = DotName.createSimple("jakarta.ws.rs.PathParam");
    private static final DotName QUERY_PARAM = DotName.createSimple("jakarta.ws.rs.QueryParam");
    private static final DotName HEADER_PARAM = DotName.createSimple("jakarta.ws.rs.HeaderParam");
    private static final DotName INJECT = DotName.createSimple("jakarta.inject.Inject");
    private static final DotName REGISTER_REST_CLIENT = DotName.createSimple(
            "org.eclipse.microprofile.rest.client.inject.RegisterRestClient");
    private static final DotName CONSUMES = DotName.createSimple("jakarta.ws.rs.Consumes");
    private static final DotName PRODUCES = DotName.createSimple("jakarta.ws.rs.Produces");
    private static final DotName CONTEXT = DotName.createSimple("jakarta.ws.rs.core.Context");
    private static final DotName HTTP_HEADERS = DotName.createSimple("jakarta.ws.rs.core.HttpHeaders");
    private static final DotName REST_FORM = DotName.createSimple("org.jboss.resteasy.reactive.RestForm");
    private static final DotName FILE_UPLOAD = DotName.createSimple("org.jboss.resteasy.reactive.multipart.FileUpload");

    private static final List<DotName> HTTP_METHODS = List.of(GET, POST, PUT, DELETE, PATCH);
    private static final List<String> HTTP_METHOD_NAMES = List.of("GET", "POST", "PUT", "DELETE", "PATCH");

    public List<RestResourceDescriptor> scan(IndexView index) {
        List<RestResourceDescriptor> result = new ArrayList<>();

        for (AnnotationInstance pathAnn : index.getAnnotations(PATH)) {
            if (pathAnn.target().kind() != AnnotationTarget.Kind.CLASS) {
                continue;
            }

            ClassInfo classInfo = pathAnn.target().asClass();

            if (classInfo.isInterface()) {
                continue;
            }
            if (classInfo.name().toString().contains(".rest.generated.")) {
                continue;
            }
            if (classInfo.declaredAnnotation(REGISTER_REST_CLIENT) != null) {
                continue;
            }

            String path = pathAnn.value().asString();
            String className = classInfo.name().toString();

            String delegateTypeName = null;
            String delegateFieldName = null;

            for (FieldInfo field : classInfo.fields()) {
                if (field.hasAnnotation(INJECT)) {
                    delegateTypeName = field.type().name().toString();
                    delegateFieldName = field.name();
                    break;
                }
            }

            if (delegateTypeName == null) {
                MethodInfo constructor = findInjectedConstructor(classInfo);
                if (constructor != null && !constructor.parameterTypes().isEmpty()) {
                    delegateTypeName = constructor.parameterTypes().get(0).name().toString();
                    delegateFieldName = constructor.parameterName(0);
                }
            }

            String[] classConsumes = extractMediaTypes(classInfo.declaredAnnotation(CONSUMES));
            String[] classProduces = extractMediaTypes(classInfo.declaredAnnotation(PRODUCES));

            boolean hasContextHeaders = false;
            for (FieldInfo field : classInfo.fields()) {
                if (field.hasAnnotation(CONTEXT) && field.type().name().equals(HTTP_HEADERS)) {
                    hasContextHeaders = true;
                    break;
                }
            }

            if (delegateTypeName == null) {
                continue;
            }

            List<RestMethodDescriptor> methods = scanMethods(classInfo, hasContextHeaders, delegateTypeName, index);

            result.add(new RestResourceDescriptor(
                    className, path, delegateTypeName, delegateFieldName,
                    methods, classConsumes, classProduces, hasContextHeaders));
        }

        return result;
    }

    private List<RestMethodDescriptor> scanMethods(ClassInfo classInfo, boolean hasContextHeaders, String delegateTypeName, IndexView index) {
        List<RestMethodDescriptor> methods = new ArrayList<>();

        ClassInfo delegateClass = delegateTypeName != null ? index.getClassByName(delegateTypeName) : null;

        for (MethodInfo method : classInfo.methods()) {
            String httpMethod = detectHttpMethod(method);
            if (httpMethod == null) {
                continue;
            }

            AnnotationInstance methodPath = method.annotation(PATH);
            String             subPath    = methodPath != null ? methodPath.value().asString() : "";

            String[] consumes = extractMediaTypes(method.annotation(CONSUMES));
            String[] produces = extractMediaTypes(method.annotation(PRODUCES));

            List<RestMethodDescriptor.ParameterDescriptor> params = new ArrayList<>();
            for (MethodParameterInfo param : method.parameters()) {
                if (findParamAnnotation(param, CONTEXT) != null) {
                    continue;
                }

                String                         paramName = param.name() != null ? param.name() : "arg" + params.size();
                com.palantir.javapoet.TypeName paramType = JandexTypeConverter.toTypeName(param.type());

                RestMethodDescriptor.ParameterSource source          = RestMethodDescriptor.ParameterSource.BODY;
                String                               annotationValue = null;

                AnnotationInstance pathParam   = findParamAnnotation(param, PATH_PARAM);
                AnnotationInstance queryParam  = findParamAnnotation(param, QUERY_PARAM);
                AnnotationInstance headerParam = findParamAnnotation(param, HEADER_PARAM);
                AnnotationInstance restForm    = findParamAnnotation(param, REST_FORM);

                if (pathParam != null) {
                    source          = RestMethodDescriptor.ParameterSource.PATH;
                    annotationValue = pathParam.value().asString();
                } else if (queryParam != null) {
                    source          = RestMethodDescriptor.ParameterSource.QUERY;
                    annotationValue = queryParam.value().asString();
                } else if (headerParam != null) {
                    source          = RestMethodDescriptor.ParameterSource.HEADER;
                    annotationValue = headerParam.value().asString();
                } else if (restForm != null && param.type().name().equals(FILE_UPLOAD)) {
                    source = RestMethodDescriptor.ParameterSource.MULTIPART;
                    annotationValue = restForm.value() != null ? restForm.value().asString() : paramName;
                }

                params.add(new RestMethodDescriptor.ParameterDescriptor(
                        paramName, paramType, source, annotationValue));
            }

            boolean needsHeaders = false;
            boolean multivaluedHeaders = false;
            com.palantir.javapoet.TypeName delegateReturnType = null;
            boolean statusBearing = false;

            if (delegateClass != null) {
                for (MethodInfo delegateMethod : delegateClass.methods()) {
                    if (delegateMethod.name().equals(method.name())) {
                        if (hasContextHeaders
                            && delegateMethod.parameterTypes().size() > method.parameterTypes().size()) {
                            needsHeaders = true;
                            org.jboss.jandex.Type extraParam = delegateMethod.parameterTypes()
                                    .get(delegateMethod.parameterTypes().size() - 1);
                            if (extraParam.kind() == org.jboss.jandex.Type.Kind.PARAMETERIZED_TYPE) {
                                var paramType = extraParam.asParameterizedType();
                                if (paramType.arguments().size() == 2) {
                                    org.jboss.jandex.Type valueType = paramType.arguments().get(1);
                                    if (valueType.kind() == org.jboss.jandex.Type.Kind.PARAMETERIZED_TYPE
                                        && valueType.name().toString().equals("java.util.List")) {
                                        multivaluedHeaders = true;
                                    }
                                }
                            }
                        }
                        boolean isResponseReturn = method.returnType().name().toString()
                                .equals("jakarta.ws.rs.core.Response");
                        if (isResponseReturn) {
                            delegateReturnType = JandexTypeConverter.toTypeName(delegateMethod.returnType());
                            statusBearing = hasStatusMethod(delegateMethod.returnType(), index);
                        }
                        break;
                    }
                }
            }

            methods.add(new RestMethodDescriptor(
                    method.name(), httpMethod, subPath,
                    JandexTypeConverter.toTypeName(method.returnType()),
                    delegateReturnType, statusBearing,
                    params, consumes, produces, needsHeaders, multivaluedHeaders));
        }

        return methods;
    }

    private String detectHttpMethod(MethodInfo method) {
        for (int i = 0; i < HTTP_METHODS.size(); i++) {
            if (method.hasAnnotation(HTTP_METHODS.get(i))) {
                return HTTP_METHOD_NAMES.get(i);
            }
        }
        return null;
    }

    private AnnotationInstance findParamAnnotation(MethodParameterInfo param, DotName annotationName) {
        for (AnnotationInstance ann : param.annotations()) {
            if (ann.name().equals(annotationName)) {
                return ann;
            }
        }
        return null;
    }

    private String[] extractMediaTypes(AnnotationInstance annotation) {
        if (annotation == null) {
            return new String[0];
        }
        AnnotationValue value = annotation.value();
        if (value == null) {
            return new String[0];
        }
        return value.asStringArray();
    }

    private boolean hasStatusMethod(org.jboss.jandex.Type type, IndexView index) {
        ClassInfo classInfo = index.getClassByName(type.name());
        if (classInfo == null) {
            return false;
        }
        for (MethodInfo method : classInfo.methods()) {
            if (method.name().equals("status")
                && method.parameterTypes().isEmpty()
                && method.returnType().kind() == org.jboss.jandex.Type.Kind.PRIMITIVE
                && method.returnType().asPrimitiveType().primitive() == org.jboss.jandex.PrimitiveType.Primitive.INT) {
                return true;
            }
        }
        return false;
    }

    private MethodInfo findInjectedConstructor(ClassInfo classInfo) {
        for (MethodInfo method : classInfo.constructors()) {
            if (method.hasAnnotation(INJECT)) {
                return method;
            }
            if (!method.parameterTypes().isEmpty() && classInfo.constructors().size() == 1) {
                return method;
            }
        }
        return null;
    }
}
