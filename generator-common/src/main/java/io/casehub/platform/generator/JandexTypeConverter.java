package io.casehub.platform.generator;

import com.palantir.javapoet.ArrayTypeName;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.WildcardTypeName;
import org.jboss.jandex.ArrayType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.ParameterizedType;
import org.jboss.jandex.PrimitiveType;
import org.jboss.jandex.Type;
import org.jboss.jandex.WildcardType;

public final class JandexTypeConverter {

    private JandexTypeConverter() {}

    public static TypeName toTypeName(Type jandexType) {
        return switch (jandexType.kind()) {
            case VOID -> TypeName.VOID;
            case PRIMITIVE -> toPrimitive((PrimitiveType) jandexType);
            case CLASS -> classNameFromDotName(jandexType.name());
            case PARAMETERIZED_TYPE -> {
                ParameterizedType pt = jandexType.asParameterizedType();
                ClassName rawType = classNameFromDotName(pt.name());
                TypeName[] typeArgs = pt.arguments().stream()
                        .map(JandexTypeConverter::toTypeName)
                        .toArray(TypeName[]::new);
                yield ParameterizedTypeName.get(rawType, typeArgs);
            }
            case ARRAY -> {
                ArrayType at = jandexType.asArrayType();
                yield ArrayTypeName.of(toTypeName(at.constituent()));
            }
            case WILDCARD_TYPE -> {
                WildcardType wt = jandexType.asWildcardType();
                if (wt.superBound() != null) {
                    yield WildcardTypeName.supertypeOf(toTypeName(wt.superBound()));
                } else if (wt.extendsBound() != null
                        && !wt.extendsBound().name().toString().equals("java.lang.Object")) {
                    yield WildcardTypeName.subtypeOf(toTypeName(wt.extendsBound()));
                }
                yield WildcardTypeName.subtypeOf(ClassName.bestGuess("java.lang.Object"));
            }
            default -> classNameFromDotName(jandexType.name());
        };
    }

    static ClassName classNameFromDotName(DotName dotName) {
        String fqcn = dotName.toString();
        if (!fqcn.contains("$")) {
            return ClassName.bestGuess(fqcn);
        }
        int dollarIndex = fqcn.indexOf('$');
        String outerFqcn = fqcn.substring(0, dollarIndex);
        ClassName outer = ClassName.bestGuess(outerFqcn);
        String[] nested = fqcn.substring(dollarIndex + 1).split("\\$");
        ClassName result = outer;
        for (String n : nested) {
            result = result.nestedClass(n);
        }
        return result;
    }

    private static TypeName toPrimitive(PrimitiveType pt) {
        return switch (pt.primitive()) {
            case BOOLEAN -> TypeName.BOOLEAN;
            case BYTE -> TypeName.BYTE;
            case SHORT -> TypeName.SHORT;
            case INT -> TypeName.INT;
            case LONG -> TypeName.LONG;
            case FLOAT -> TypeName.FLOAT;
            case DOUBLE -> TypeName.DOUBLE;
            case CHAR -> TypeName.CHAR;
        };
    }
}
