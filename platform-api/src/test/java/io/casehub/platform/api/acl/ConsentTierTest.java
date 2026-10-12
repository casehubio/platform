package io.casehub.platform.api.acl;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ConsentTierTest {

    @Test
    void hasFourValues() {
        assertEquals(4, ConsentTier.values().length);
    }

    @Test
    void valuesInExpectedOrder() {
        assertArrayEquals(
            new ConsentTier[] {
                ConsentTier.AUTO,
                ConsentTier.CONFIRM,
                ConsentTier.APPROVE_WITH_DETAILS,
                ConsentTier.DENY
            },
            ConsentTier.values());
    }
}
