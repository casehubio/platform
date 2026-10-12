package io.casehub.platform.api.acl;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ConsentOutcomeTest {

    @Test
    void hasFourValues() {
        assertEquals(4, ConsentOutcome.values().length);
    }

    @Test
    void valuesInExpectedOrder() {
        assertArrayEquals(
            new ConsentOutcome[] {
                ConsentOutcome.APPROVED,
                ConsentOutcome.NEEDS_CONFIRMATION,
                ConsentOutcome.NEEDS_APPROVAL,
                ConsentOutcome.DENIED
            },
            ConsentOutcome.values());
    }
}
