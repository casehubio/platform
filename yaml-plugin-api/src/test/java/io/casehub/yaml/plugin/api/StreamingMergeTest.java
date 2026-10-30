package io.casehub.yaml.plugin.api;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StreamingMergeTest {

    @Test
    void mergeTypeDeclaresMergedType() {
        StreamingMerge<InputStream> merge = new StreamingMerge<>() {
            @Override public Class<InputStream> mergeType() { return InputStream.class; }
            @Override public InputStream merge(List<InputStream> inputs) {
                return new SequenceInputStream(Collections.enumeration(inputs));
            }
        };
        assertEquals(InputStream.class, merge.mergeType());
    }

    @Test
    void mergeAcceptsMultipleInputs() {
        StreamingMerge<String> merge = new StreamingMerge<>() {
            @Override public Class<String> mergeType() { return String.class; }
            @Override public String merge(List<String> inputs) {
                return String.join("|", inputs);
            }
        };
        assertEquals("a|b|c", merge.merge(List.of("a", "b", "c")));
    }

    @Test
    void mergeSingleInput() {
        StreamingMerge<String> merge = new StreamingMerge<>() {
            @Override public Class<String> mergeType() { return String.class; }
            @Override public String merge(List<String> inputs) {
                return String.join("|", inputs);
            }
        };
        assertEquals("only", merge.merge(List.of("only")));
    }
}
