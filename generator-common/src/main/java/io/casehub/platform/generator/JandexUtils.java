package io.casehub.platform.generator;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;

import java.util.Optional;

public final class JandexUtils {

    private JandexUtils() {}

    public static Optional<String> annotationStringValue(AnnotationInstance annotation, String name) {
        AnnotationValue val = annotation.valueWithDefault(null, name);
        return val != null ? Optional.ofNullable(val.asString()) : Optional.empty();
    }

    public static Optional<String[]> annotationStringArrayValue(AnnotationInstance annotation, String name) {
        AnnotationValue val = annotation.valueWithDefault(null, name);
        return val != null ? Optional.ofNullable(val.asStringArray()) : Optional.empty();
    }

    public static boolean hasAnnotation(ClassInfo classInfo, DotName annotationName) {
        return classInfo.annotation(annotationName) != null;
    }

    public static boolean hasAnnotation(ClassInfo classInfo, String annotationFqn) {
        return hasAnnotation(classInfo, DotName.createSimple(annotationFqn));
    }
}
