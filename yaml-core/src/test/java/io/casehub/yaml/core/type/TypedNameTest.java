package io.casehub.yaml.core.type;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TypedNameTest {

    @Test
    void parse_with_type_suffix() {
        TypedName tn = TypedName.parse("batch_size:integer");
        assertThat(tn.name()).isEqualTo("batch_size");
        assertThat(tn.scalarType()).isEqualTo(ValueType.INTEGER);
    }

    @Test
    void parse_without_type_defaults_to_string() {
        TypedName tn = TypedName.parse("bucket");
        assertThat(tn.name()).isEqualTo("bucket");
        assertThat(tn.scalarType()).isEqualTo(ValueType.STRING);
    }

    @Test
    void parse_trims_whitespace() {
        TypedName tn = TypedName.parse("  name : STRING  ");
        assertThat(tn.name()).isEqualTo("name");
        assertThat(tn.scalarType()).isEqualTo(ValueType.STRING);
    }

    @Test
    void parse_case_insensitive_type() {
        assertThat(TypedName.parse("x:boolean").scalarType()).isEqualTo(ValueType.BOOLEAN);
        assertThat(TypedName.parse("x:BOOLEAN").scalarType()).isEqualTo(ValueType.BOOLEAN);
        assertThat(TypedName.parse("x:Boolean").scalarType()).isEqualTo(ValueType.BOOLEAN);
    }

    @Test
    void parse_unknown_scalar_becomes_domain_type() {
        TypedName tn = TypedName.parse("x:BLOB");
        assertThat(tn.name()).isEqualTo("x");
        assertThat(tn.typeName()).isEqualTo("BLOB");
        assertThat(tn.scalarType()).isNull();
        assertThat(tn.isScalar()).isFalse();
    }

    @Test
    void parse_empty_type_throws() {
        assertThatThrownBy(() -> TypedName.parse("x:"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Missing type");
    }

    @Test
    void blank_name_throws() {
        assertThatThrownBy(() -> TypedName.parse(":INTEGER"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
    }

    @Test
    void all_four_types() {
        assertThat(TypedName.parse("a:string").scalarType()).isEqualTo(ValueType.STRING);
        assertThat(TypedName.parse("b:integer").scalarType()).isEqualTo(ValueType.INTEGER);
        assertThat(TypedName.parse("c:boolean").scalarType()).isEqualTo(ValueType.BOOLEAN);
        assertThat(TypedName.parse("d:number").scalarType()).isEqualTo(ValueType.NUMBER);
    }

    @Test
    void parse_domain_type_has_null_scalarType() {
        TypedName tn = TypedName.parse("config:AgentSessionConfig");
        assertThat(tn.name()).isEqualTo("config");
        assertThat(tn.typeName()).isEqualTo("AgentSessionConfig");
        assertThat(tn.scalarType()).isNull();
        assertThat(tn.isScalar()).isFalse();
    }

    @Test
    void parse_scalar_type_populates_both_fields() {
        TypedName tn = TypedName.parse("count:integer");
        assertThat(tn.name()).isEqualTo("count");
        assertThat(tn.typeName()).isEqualTo("integer");
        assertThat(tn.scalarType()).isEqualTo(ValueType.INTEGER);
        assertThat(tn.isScalar()).isTrue();
    }

    @Test
    void parse_untyped_defaults_to_string_with_typeName() {
        TypedName tn = TypedName.parse("bucket");
        assertThat(tn.name()).isEqualTo("bucket");
        assertThat(tn.typeName()).isEqualTo("string");
        assertThat(tn.scalarType()).isEqualTo(ValueType.STRING);
        assertThat(tn.isScalar()).isTrue();
    }

}
