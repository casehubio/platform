package io.casehub.platform.spring.generator;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.MethodParameterInfo;
import org.jboss.jandex.Type;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class JandexProducerScanner {

    private static final DotName PRODUCES = DotName.createSimple("jakarta.enterprise.inject.Produces");
    private static final DotName DEFAULT_BEAN = DotName.createSimple("io.quarkus.arc.DefaultBean");
    private static final DotName ALTERNATIVE = DotName.createSimple("jakarta.enterprise.inject.Alternative");
    private static final DotName PRIORITY = DotName.createSimple("jakarta.annotation.Priority");
    private static final DotName CONFIG_PROPERTY = DotName.createSimple("org.eclipse.microprofile.config.inject.ConfigProperty");
    private static final DotName CDI_INSTANCE = DotName.createSimple("jakarta.enterprise.inject.Instance");
    private static final DotName CDI_EVENT = DotName.createSimple("jakarta.enterprise.event.Event");
    private static final DotName INJECT = DotName.createSimple("jakarta.inject.Inject");
    private static final DotName CONFIG_MAPPING = DotName.createSimple("io.smallrye.config.ConfigMapping");
    private static final DotName FACTORY_METHOD = DotName.createSimple("io.casehub.platform.api.FactoryMethod");
    private static final DotName QUALIFIER = DotName.createSimple("jakarta.inject.Qualifier");
    private static final DotName POST_CONSTRUCT = DotName.createSimple("jakarta.annotation.PostConstruct");
    private static final DotName JAVA_LIST = DotName.createSimple("java.util.List");
    private static final DotName JAVA_OPTIONAL = DotName.createSimple("java.util.Optional");
    private static final DotName WITH_DEFAULT = DotName.createSimple("io.smallrye.config.WithDefault");

    private static final Set<DotName> KNOWN_METHOD_ANNOTATIONS = Set.of(
            PRODUCES, DEFAULT_BEAN, ALTERNATIVE, PRIORITY,
            DotName.createSimple("jakarta.enterprise.context.ApplicationScoped"),
            DotName.createSimple("jakarta.inject.Singleton"),
            DotName.createSimple("jakarta.enterprise.context.Dependent"),
            DotName.createSimple("jakarta.enterprise.context.RequestScoped"));

    public List<ProducerDescriptor> scan(IndexView index) {
        return scan(index, index);
    }

    public List<ProducerDescriptor> scan(IndexView scanIndex, IndexView resolveIndex) {
        List<ProducerDescriptor> result = new ArrayList<>();

        for (AnnotationInstance produces : scanIndex.getAnnotations(PRODUCES)) {
            if (produces.target().kind() != org.jboss.jandex.AnnotationTarget.Kind.METHOD) {
                continue;
            }

            MethodInfo method         = produces.target().asMethod();
            ClassInfo  declaringClass = method.declaringClass();

            boolean            isDefaultBean = method.hasAnnotation(DEFAULT_BEAN);
            boolean            isAlternative = method.hasAnnotation(ALTERNATIVE);
            int                priorityVal   = 0;
            AnnotationInstance priorityAnn   = method.annotation(PRIORITY);
            if (priorityAnn != null) {
                priorityVal = priorityAnn.value().asInt();
            }

            boolean hasCdiDeps = false;
            String concreteReturnType = null;

            DotName returnTypeName = method.returnType().name();
            if (returnTypeName.toString().startsWith("java.")) {
                hasCdiDeps = true;
            }

            ClassInfo returnTypeInfo = resolveIndex.getClassByName(returnTypeName);
            if (returnTypeInfo != null && isAbstractOrInterface(returnTypeInfo)) {
                concreteReturnType = resolveConcreteType(resolveIndex, method.name(), returnTypeInfo);
                if (concreteReturnType == null) {
                    hasCdiDeps = true;
                }
            }

            if (declaringClass.fields().stream()
                              .anyMatch(f -> f.hasAnnotation(INJECT) || f.hasAnnotation(CONFIG_PROPERTY))) {
                hasCdiDeps = true;
            }
            if (declaringClass.constructors().stream()
                              .anyMatch(c -> c.hasAnnotation(INJECT))) {
                hasCdiDeps = true;
            }

            if (hasUnknownCdiQualifiers(method)) {
                hasCdiDeps = true;
            }

            List<ProducerDescriptor.ParameterDescriptor> params = new ArrayList<>();
            List<String> qualifiers = new ArrayList<>();

            for (MethodParameterInfo param : method.parameters()) {
                DotName typeName = param.type().kind() == Type.Kind.PARAMETERIZED_TYPE
                                   ? param.type().asParameterizedType().name()
                                   : param.type().name();

                if (CDI_INSTANCE.equals(typeName) || CDI_EVENT.equals(typeName)) {
                    hasCdiDeps = true;
                    collectQualifiers(param, resolveIndex, qualifiers);
                }

                ClassInfo paramTypeInfo = resolveIndex.getClassByName(typeName);
                if (paramTypeInfo != null && paramTypeInfo.hasAnnotation(CONFIG_MAPPING)) {
                    hasCdiDeps = true;
                }

                boolean hasQuarkusQualifier = param.annotations().stream()
                                                   .anyMatch(a -> a.name().toString().startsWith("io.quarkus."));
                if (hasQuarkusQualifier) {
                    hasCdiDeps = true;
                }

                String paramType = param.type().name().toString();
                String paramName = param.name() != null ? param.name() : inferParamName(paramType);

                String             configProp = null;
                AnnotationInstance configAnn  = param.annotation(CONFIG_PROPERTY);
                if (configAnn != null && configAnn.value("name") != null) {
                    configProp = configAnn.value("name").asString();
                }

                params.add(new ProducerDescriptor.ParameterDescriptor(paramType, paramName, configProp));
            }

            // --- Constructor following: resolve core POJO constructor ---
            List<ProducerDescriptor.ConstructorParam> constructorParams = null;
            String configPrefix = null;
            String configInterface = null;
            boolean hasFactory = false;
            String factoryName = null;
            List<ProducerDescriptor.ConfigPropertyMethod> configMethods = List.of();

            ClassInfo effectiveType = concreteReturnType != null
                    ? resolveIndex.getClassByName(DotName.createSimple(concreteReturnType))
                    : returnTypeInfo;

            if (effectiveType != null && !isAbstractOrInterface(effectiveType)
                    && !returnTypeName.toString().startsWith("java.")) {

                MethodInfo targetMethod = findFactoryMethod(effectiveType);
                if (targetMethod != null) {
                    hasFactory = true;
                    factoryName = targetMethod.name();
                } else {
                    targetMethod = findConstructor(effectiveType);
                }

                if (targetMethod != null) {
                    var ctorParams = new ArrayList<ProducerDescriptor.ConstructorParam>();
                    for (int i = 0; i < targetMethod.parametersCount(); i++) {
                        Type paramType = targetMethod.parameterType(i);
                        String pName = targetMethod.parameterName(i);
                        if (pName == null) {
                            pName = "arg" + i;
                        }
                        var result2 = resolveParamKind(paramType, scanIndex, resolveIndex);
                        ctorParams.add(new ProducerDescriptor.ConstructorParam(
                                result2.type, pName, result2.kind));
                        if (result2.kind == ProducerDescriptor.ParamKind.CONFIG_PROPERTIES) {
                            configPrefix = result2.configPrefix;
                            configInterface = result2.configInterface;
                            configMethods = collectConfigMethods(
                                    DotName.createSimple(result2.configInterface),
                                    scanIndex, resolveIndex);
                        }
                    }
                    constructorParams = List.copyOf(ctorParams);
                }
            }

            // Detect @PostConstruct init method on declaring class
            String initMethod = null;
            for (MethodInfo m : declaringClass.methods()) {
                if (m.hasAnnotation(POST_CONSTRUCT)) {
                    initMethod = m.name();
                    break;
                }
            }

            // Detect if composite needs @Primary (takes qualified List of same type)
            boolean primaryBean = false;
            if (!qualifiers.isEmpty()) {
                primaryBean = true;
            }

            // Order value from @Priority on the method
            int orderValue = priorityVal;

            result.add(new ProducerDescriptor(
                    declaringClass.name().toString(),
                    method.name(),
                    method.returnType().name().toString(),
                    concreteReturnType,
                    params,
                    isDefaultBean,
                    isAlternative,
                    priorityVal,
                    hasCdiDeps,
                    constructorParams,
                    configPrefix,
                    configInterface,
                    qualifiers,
                    initMethod,
                    primaryBean,
                    orderValue,
                    hasFactory,
                    factoryName,
                    configMethods));
        }

        return result;
    }

    private record ParamResolution(String type, ProducerDescriptor.ParamKind kind,
                                   String configPrefix, String configInterface) {}

    private ParamResolution resolveParamKind(Type paramType, IndexView scanIndex, IndexView resolveIndex) {
        if (paramType.kind() == Type.Kind.PARAMETERIZED_TYPE) {
            DotName rawName = paramType.asParameterizedType().name();
            if (JAVA_LIST.equals(rawName) && !paramType.asParameterizedType().arguments().isEmpty()) {
                String typeArg = paramType.asParameterizedType().arguments().get(0).name().toString();
                return new ParamResolution(typeArg, ProducerDescriptor.ParamKind.LIST, null, null);
            }
            if (JAVA_OPTIONAL.equals(rawName) && !paramType.asParameterizedType().arguments().isEmpty()) {
                String typeArg = paramType.asParameterizedType().arguments().get(0).name().toString();
                return new ParamResolution(typeArg, ProducerDescriptor.ParamKind.OPTIONAL, null, null);
            }
        }

        DotName typeName = paramType.name();
        var configResult = findConfigMapping(typeName, scanIndex, resolveIndex);
        if (configResult != null) {
            return new ParamResolution(typeName.toString(),
                    ProducerDescriptor.ParamKind.CONFIG_PROPERTIES,
                    configResult.prefix, configResult.interfaceName);
        }

        return new ParamResolution(typeName.toString(), ProducerDescriptor.ParamKind.PLAIN, null, null);
    }

    private record ConfigMappingResult(String prefix, String interfaceName) {}

    private ConfigMappingResult findConfigMapping(DotName typeName, IndexView scanIndex, IndexView resolveIndex) {
        ClassInfo typeInfo = resolveIndex.getClassByName(typeName);
        if (typeInfo == null) {
            typeInfo = scanIndex.getClassByName(typeName);
        }
        if (typeInfo == null) {
            return null;
        }

        if (typeInfo.hasAnnotation(CONFIG_MAPPING)) {
            AnnotationInstance ann = typeInfo.annotation(CONFIG_MAPPING);
            String prefix = ann.value("prefix") != null ? ann.value("prefix").asString() : "";
            return new ConfigMappingResult(prefix, typeName.toString());
        }

        // Walk UP: check superinterfaces and superclass
        for (DotName iface : typeInfo.interfaceNames()) {
            var result = findConfigMapping(iface, scanIndex, resolveIndex);
            if (result != null) {
                return result;
            }
        }

        DotName superName = typeInfo.superName();
        if (superName != null && !superName.toString().equals("java.lang.Object")) {
            var result = findConfigMapping(superName, scanIndex, resolveIndex);
            if (result != null) {
                return result;
            }
        }

        // Walk DOWN: scan all @ConfigMapping-annotated types and check if any extend this type
        // (e.g., SampleProperties is extended by SampleConfig which has @ConfigMapping)
        for (AnnotationInstance cmAnn : scanIndex.getAnnotations(CONFIG_MAPPING)) {
            if (cmAnn.target().kind() == org.jboss.jandex.AnnotationTarget.Kind.CLASS) {
                ClassInfo cmClass = cmAnn.target().asClass();
                if (cmClass.interfaceNames().contains(typeName) || typeName.equals(cmClass.superName())) {
                    String prefix = cmAnn.value("prefix") != null ? cmAnn.value("prefix").asString() : "";
                    return new ConfigMappingResult(prefix, typeName.toString());
                }
            }
        }

        return null;
    }

    private List<ProducerDescriptor.ConfigPropertyMethod> collectConfigMethods(
            DotName propertiesInterface, IndexView scanIndex, IndexView resolveIndex) {
        var methods = new ArrayList<ProducerDescriptor.ConfigPropertyMethod>();
        ClassInfo propsClass = resolveIndex.getClassByName(propertiesInterface);
        if (propsClass == null) {
            propsClass = scanIndex.getClassByName(propertiesInterface);
        }
        if (propsClass == null) {
            return methods;
        }

        // Find the @ConfigMapping extension to read @WithDefault values
        ClassInfo configMappingClass = null;
        for (AnnotationInstance cmAnn : scanIndex.getAnnotations(CONFIG_MAPPING)) {
            if (cmAnn.target().kind() == org.jboss.jandex.AnnotationTarget.Kind.CLASS) {
                ClassInfo cmClass = cmAnn.target().asClass();
                if (cmClass.interfaceNames().contains(propertiesInterface)
                        || propertiesInterface.equals(cmClass.name())) {
                    configMappingClass = cmClass;
                    break;
                }
            }
        }

        for (MethodInfo m : propsClass.methods()) {
            if (m.parametersCount() == 0
                    && !m.name().equals("<init>")
                    && !m.name().equals("<clinit>")
                    && java.lang.reflect.Modifier.isAbstract(m.flags())) {
                String defaultValue = null;
                if (configMappingClass != null) {
                    MethodInfo configMethod = configMappingClass.method(m.name());
                    if (configMethod != null && configMethod.hasAnnotation(WITH_DEFAULT)) {
                        defaultValue = configMethod.annotation(WITH_DEFAULT).value().asString();
                    }
                }
                methods.add(new ProducerDescriptor.ConfigPropertyMethod(
                        m.name(), m.returnType().name().toString(), defaultValue));
            }
        }
        return methods;
    }

    private MethodInfo findFactoryMethod(ClassInfo classInfo) {
        for (MethodInfo method : classInfo.methods()) {
            if (method.hasAnnotation(FACTORY_METHOD)
                    && java.lang.reflect.Modifier.isStatic(method.flags())
                    && java.lang.reflect.Modifier.isPublic(method.flags())) {
                return method;
            }
        }
        return null;
    }

    private MethodInfo findConstructor(ClassInfo classInfo) {
        List<MethodInfo> constructors = classInfo.constructors();
        if (constructors.size() == 1) {
            return constructors.get(0);
        }
        for (MethodInfo ctor : constructors) {
            if (java.lang.reflect.Modifier.isPublic(ctor.flags()) && ctor.parametersCount() > 0) {
                return ctor;
            }
        }
        return constructors.isEmpty() ? null : constructors.get(0);
    }

    private void collectQualifiers(MethodParameterInfo param, IndexView resolveIndex, List<String> qualifiers) {
        for (AnnotationInstance ann : param.annotations()) {
            ClassInfo annClass = resolveIndex.getClassByName(ann.name());
            if (annClass != null && annClass.hasAnnotation(QUALIFIER)) {
                qualifiers.add(ann.name().toString());
            }
        }
    }

    private String inferParamName(String typeName) {
        String simple = typeName.substring(typeName.lastIndexOf('.') + 1);
        return Character.toLowerCase(simple.charAt(0)) + simple.substring(1);
    }

    private boolean hasUnknownCdiQualifiers(MethodInfo method) {
        for (AnnotationInstance ann : method.annotations()) {
            if (ann.target().kind() != org.jboss.jandex.AnnotationTarget.Kind.METHOD) {
                continue;
            }
            DotName name = ann.name();
            if (!KNOWN_METHOD_ANNOTATIONS.contains(name)
                && !name.toString().startsWith("java.")
                && !name.toString().startsWith("javax.")) {
                return true;
            }
        }
        return false;
    }

    private boolean isAbstractOrInterface(ClassInfo classInfo) {
        return java.lang.reflect.Modifier.isInterface(classInfo.flags())
                || java.lang.reflect.Modifier.isAbstract(classInfo.flags());
    }

    private String resolveConcreteType(IndexView index, String methodName, ClassInfo abstractType) {
        String pascalName = Character.toUpperCase(methodName.charAt(0)) + methodName.substring(1);
        for (ClassInfo impl : index.getAllKnownImplementors(abstractType.name())) {
            if (impl.simpleName().equals(pascalName)) {
                return impl.name().toString();
            }
        }
        for (ClassInfo sub : index.getAllKnownSubclasses(abstractType.name())) {
            if (sub.simpleName().equals(pascalName)) {
                return sub.name().toString();
            }
        }
        return null;
    }

}
