package io.casehub.platform.generator;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JandexTypeConverterTest {

    private static Index index;

    @BeforeAll
    static void buildIndex() throws Exception {
        var indexer = new Indexer();
        indexer.indexClass(SampleTypes.class);
        indexer.indexClass(SampleTypes.Inner.class);
        indexer.indexClass(SampleTypes.Outer.class);
        indexer.indexClass(SampleTypes.Outer.Nested.class);
        indexer.indexClass(SampleTypes.Outer.Nested.Deep.class);
        index = indexer.complete();
    }

    private org.jboss.jandex.Type returnTypeOf(String methodName) {
        ClassInfo ci = index.getClassByName(DotName.createSimple(SampleTypes.class.getName()));
        return ci.methods().stream()
                .filter(m -> m.name().equals(methodName))
                .findFirst().orElseThrow()
                .returnType();
    }

    @Test
    void convertsSimpleClass() {
        TypeName result = JandexTypeConverter.toTypeName(returnTypeOf("simpleString"));
        assertThat(result).isEqualTo(ClassName.bestGuess("java.lang.String"));
    }

    @Test
    void convertsParameterizedType() {
        TypeName result = JandexTypeConverter.toTypeName(returnTypeOf("parameterized"));
        assertThat(result).isInstanceOf(ParameterizedTypeName.class);
        assertThat(result.toString()).isEqualTo("java.util.List<java.lang.String>");
    }

    @Test
    void convertsNestedParameterizedType() {
        TypeName result = JandexTypeConverter.toTypeName(returnTypeOf("nested"));
        assertThat(result.toString()).isEqualTo("java.util.Map<java.lang.String, java.util.List<java.lang.Integer>>");
    }

    @Test
    void convertsVoid() {
        TypeName result = JandexTypeConverter.toTypeName(returnTypeOf("voidReturn"));
        assertThat(result).isEqualTo(TypeName.VOID);
    }

    @Test
    void convertsPrimitive() {
        TypeName result = JandexTypeConverter.toTypeName(returnTypeOf("primitiveInt"));
        assertThat(result).isEqualTo(TypeName.INT);
    }

    @Test
    void convertsArray() {
        TypeName result = JandexTypeConverter.toTypeName(returnTypeOf("arrayReturn"));
        assertThat(result.toString()).isEqualTo("java.lang.String[]");
    }

    @Test
    void convertsInnerClass() {
        TypeName result = JandexTypeConverter.toTypeName(returnTypeOf("innerClass"));
        assertThat(result).isInstanceOf(ClassName.class);
        assertThat(result.toString()).isEqualTo(
                "io.casehub.platform.generator.SampleTypes.Inner");
    }

    @Test
    void convertsNestedInnerClass() {
        TypeName result = JandexTypeConverter.toTypeName(returnTypeOf("nestedInnerClass"));
        assertThat(result.toString()).isEqualTo(
                "io.casehub.platform.generator.SampleTypes.Outer.Nested");
    }

    @Test
    void convertsDeeplyNestedInnerClass() {
        TypeName result = JandexTypeConverter.toTypeName(returnTypeOf("deeplyNestedInnerClass"));
        assertThat(result.toString()).isEqualTo(
                "io.casehub.platform.generator.SampleTypes.Outer.Nested.Deep");
    }

    @Test
    void convertsListOfInnerClass() {
        TypeName result = JandexTypeConverter.toTypeName(returnTypeOf("listOfInnerClass"));
        assertThat(result.toString()).isEqualTo(
                "java.util.List<io.casehub.platform.generator.SampleTypes.Inner>");
    }

    @Test
    void convertsMapWithInnerClassValue() {
        TypeName result = JandexTypeConverter.toTypeName(returnTypeOf("mapWithInnerClassValue"));
        assertThat(result.toString()).isEqualTo(
                "java.util.Map<java.lang.String, io.casehub.platform.generator.SampleTypes.Inner>");
    }


}
