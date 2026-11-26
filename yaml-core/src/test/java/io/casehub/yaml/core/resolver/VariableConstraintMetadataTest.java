package io.casehub.yaml.core.resolver;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class VariableConstraintMetadataTest {

    @Test
    void stringConstraint_defaultMetadata_isEmpty() {
        var c = VariableConstraint.string();
        assertEquals(Map.of(), c.metadata());
    }

    @Test
    void stringConstraint_withMetadata_returnsMetadata() {
        var meta = Map.of(MetadataKeys.PERSON, "3rd", MetadataKeys.PRONOUNS, "they/them");
        var c = VariableConstraint.string().withMetadata(meta);
        assertEquals("3rd", c.metadata().get(MetadataKeys.PERSON));
        assertEquals("they/them", c.metadata().get(MetadataKeys.PRONOUNS));
        assertTrue(c.required());
    }

    @Test
    void stringConstraint_withMetadata_preservesRequired() {
        var c = VariableConstraint.string().optional().withMetadata(Map.of("k", "v"));
        assertFalse(c.required());
        assertEquals("v", c.metadata().get("k"));
    }

    @Test
    void stringConstraint_optional_preservesMetadata() {
        var c = VariableConstraint.string().withMetadata(Map.of("k", "v")).optional();
        assertFalse(c.required());
        assertEquals("v", c.metadata().get("k"));
    }

    @Test
    void stringConstraint_metadataIsImmutable() {
        var c = VariableConstraint.string().withMetadata(Map.of("k", "v"));
        assertThrows(UnsupportedOperationException.class, () -> c.metadata().put("x", "y"));
    }

    @Test
    void enumConstraint_defaultMetadata_isEmpty() {
        var c = VariableConstraint.enumOf("a", "b");
        assertEquals(Map.of(), c.metadata());
    }

    @Test
    void enumConstraint_withMetadata_returnsMetadata() {
        var c = VariableConstraint.enumOf("a", "b")
            .withMetadata(Map.of(MetadataKeys.NUMBER, "singular"));
        assertEquals("singular", c.metadata().get(MetadataKeys.NUMBER));
    }

    @Test
    void enumConstraint_optional_preservesMetadata() {
        var c = VariableConstraint.enumOf("a", "b")
            .withMetadata(Map.of("k", "v")).optional();
        assertFalse(c.required());
        assertEquals("v", c.metadata().get("k"));
    }

    @Test
    void enumConstraint_metadataIsImmutable() {
        var c = VariableConstraint.enumOf("a", "b").withMetadata(Map.of("k", "v"));
        assertThrows(UnsupportedOperationException.class, () -> c.metadata().put("x", "y"));
    }

    @Test
    void nullMetadata_defaultsToEmpty() {
        var c = new VariableConstraint.StringConstraint(true, null);
        assertEquals(Map.of(), c.metadata());
    }

    @Test
    void metadataKeys_constantsAreDefined() {
        assertEquals("person", MetadataKeys.PERSON);
        assertEquals("number", MetadataKeys.NUMBER);
        assertEquals("pronouns", MetadataKeys.PRONOUNS);
    }
}
