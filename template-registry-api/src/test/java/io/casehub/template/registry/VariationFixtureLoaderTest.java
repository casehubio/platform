package io.casehub.template.registry;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VariationFixtureLoaderTest {

    @Test
    void load_fromInputStream() {
        InputStream is = getClass().getResourceAsStream("/fixtures/test-fixtures.variations.yaml");
        List<VariationFixture> fixtures = VariationFixtureLoader.load(is);

        assertThat(fixtures).hasSize(3);
        assertThat(fixtures.get(0).templateName()).isEqualTo("hello");
        assertThat(fixtures.get(0).input()).isEqualTo("hi there friend");
        assertThat(fixtures.get(0).expectMatch()).isTrue();
        assertThat(fixtures.get(0).expectedFields()).containsEntry("target", "friend");
    }

    @Test
    void load_negativeFixtureHasEmptyFields() {
        InputStream is = getClass().getResourceAsStream("/fixtures/test-fixtures.variations.yaml");
        List<VariationFixture> fixtures = VariationFixtureLoader.load(is);

        var negative = fixtures.get(1);
        assertThat(negative.expectMatch()).isFalse();
        assertThat(negative.expectedFields()).isEmpty();
    }

    @Test
    void namespace_readsFromFile() throws Exception {
        InputStream is = getClass().getResourceAsStream("/fixtures/test-fixtures.variations.yaml");
        Path tmp = Files.createTempFile("fixtures", ".yaml");
        try {
            Files.write(tmp, is.readAllBytes());
            String ns = VariationFixtureLoader.namespace(tmp);
            assertThat(ns).isEqualTo("test:greetings");
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    void load_fromPath() throws Exception {
        InputStream is = getClass().getResourceAsStream("/fixtures/test-fixtures.variations.yaml");
        Path tmp = Files.createTempFile("fixtures", ".yaml");
        try {
            Files.write(tmp, is.readAllBytes());
            List<VariationFixture> fixtures = VariationFixtureLoader.load(tmp);
            assertThat(fixtures).hasSize(3);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
