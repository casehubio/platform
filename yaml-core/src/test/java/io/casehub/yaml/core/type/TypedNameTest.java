package io.casehub.yaml.core.type;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TypedNameTest {

    @Test
    void parse_with_type_suffix() {
        TypedName tn = TypedName.parse("batch_size:integer");
        assertThat(tn.name()).isEqualTo("batch_size");
        assertThat(tn.type()).isEqualTo(ValueType.INTEGER);
    }

    @Test
    void parse_without_type_defaults_to_string() {
        TypedName tn = TypedName.parse("bucket");
        assertThat(tn.name()).isEqualTo("bucket");
        assertThat(tn.type()).isEqualTo(ValueType.STRING);
    }

    @Test
    void parse_trims_whitespace() {
        TypedName tn = TypedName.parse("  name : STRING  ");
        assertThat(tn.name()).isEqualTo("name");
        assertThat(tn.type()).isEqualTo(ValueType.STRING);
    }

    @Test
    void parse_case_insensitive_type() {
        assertThat(TypedName.parse("x:boolean").type()).isEqualTo(ValueType.BOOLEAN);
        assertThat(TypedName.parse("x:BOOLEAN").type()).isEqualTo(ValueType.BOOLEAN);
        assertThat(TypedName.parse("x:Boolean").type()).isEqualTo(ValueType.BOOLEAN);
    }

    @Test
    void parse_unknown_type_throws() {
        assertThatThrownBy(() -> TypedName.parse("x:BLOB"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BLOB")
                .hasMessageContaining("STRING, INTEGER, BOOLEAN, NUMBER");
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
        assertThat(TypedName.parse("a:string").type()).isEqualTo(ValueType.STRING);
        assertThat(TypedName.parse("b:integer").type()).isEqualTo(ValueType.INTEGER);
        assertThat(TypedName.parse("c:boolean").type()).isEqualTo(ValueType.BOOLEAN);
        assertThat(TypedName.parse("d:number").type()).isEqualTo(ValueType.NUMBER);
    }
}
