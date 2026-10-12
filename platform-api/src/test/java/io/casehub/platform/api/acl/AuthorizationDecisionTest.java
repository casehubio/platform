package io.casehub.platform.api.acl;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class AuthorizationDecisionTest {

    @Test
    void approve_returnsApprovedOutcome() {
        var decision = AuthorizationDecision.approve();
        assertEquals(ConsentOutcome.APPROVED, decision.outcome());
        assertNull(decision.reason());
    }

    @Test
    void approve_backwardCompatApprovedReturnsTrue() {
        var decision = AuthorizationDecision.approve();
        assertTrue(decision.approved());
    }

    @Test
    void deny_returnsDeniedOutcome() {
        var decision = AuthorizationDecision.deny("blocked by policy");
        assertEquals(ConsentOutcome.DENIED, decision.outcome());
        assertEquals("blocked by policy", decision.reason());
    }

    @Test
    void deny_backwardCompatApprovedReturnsFalse() {
        var decision = AuthorizationDecision.deny("reason");
        assertFalse(decision.approved());
    }

    @Test
    void needsConfirmation_returnsCorrectOutcome() {
        var decision = AuthorizationDecision.needsConfirmation("confirm booking");
        assertEquals(ConsentOutcome.NEEDS_CONFIRMATION, decision.outcome());
        assertEquals("confirm booking", decision.reason());
        assertFalse(decision.approved());
    }

    @Test
    void needsApproval_returnsCorrectOutcome() {
        var decision = AuthorizationDecision.needsApproval("purchase requires approval");
        assertEquals(ConsentOutcome.NEEDS_APPROVAL, decision.outcome());
        assertEquals("purchase requires approval", decision.reason());
        assertFalse(decision.approved());
    }
}
