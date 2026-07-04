package io.casehub.platform.api.datasource;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClassObjectTypeTest {

    @Test
    void matches_exactType() {
        ClassObjectType<String> type = new ClassObjectType<>(String.class);
        assertThat(type.matches("hello")).isTrue();
        assertThat(type.matches(42)).isFalse();
        assertThat(type.matches(null)).isFalse();
    }

    @Test
    void matches_subtype() {
        ClassObjectType<Number> type = new ClassObjectType<>(Number.class);
        assertThat(type.matches(42)).isTrue();
        assertThat(type.matches(3.14)).isTrue();
        assertThat(type.matches("not a number")).isFalse();
    }

    @Test
    void getTypeKey_returnsClass() {
        ClassObjectType<String> type = new ClassObjectType<>(String.class);
        assertThat(type.getTypeKey()).isEqualTo(String.class);
    }

    @Test
    void equals_sameClass() {
        ClassObjectType<String> a = new ClassObjectType<>(String.class);
        ClassObjectType<String> b = new ClassObjectType<>(String.class);
        assertThat(a.getTypeKey()).isEqualTo(b.getTypeKey());
    }
}
