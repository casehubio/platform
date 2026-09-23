package io.casehub.yaml.core.orchestration;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Showcase: state machine driven approval workflow.
 *
 * Equivalent YAML:
 * <pre>
 * stateMachine:
 *   name: approval
 *   initial: draft
 *   transitions:
 *     - from: draft
 *       to: submitted
 *       on: submit
 *     - from: submitted
 *       to: approved
 *       on: approve
 *       when: ${approver-count} >= 2
 *     - from: submitted
 *       to: rejected
 *       on: reject
 *     - from: approved
 *       to: published
 *       on: publish
 *   terminal: [rejected, published]
 * </pre>
 */
class ShowcaseStateMachineWorkflowTest {

    enum ApprovalState { DRAFT, SUBMITTED, APPROVED, REJECTED, PUBLISHED }

    @Test
    void approvalWorkflow_happyPath() {
        var sm = DefaultOrcStateMachine.builder("approval", ApprovalState.class, ApprovalState.DRAFT)
                .transition(ApprovalState.DRAFT, ApprovalState.SUBMITTED)
                .transition(ApprovalState.SUBMITTED, ApprovalState.APPROVED,
                        payload -> payload instanceof Integer count && count >= 2)
                .transition(ApprovalState.SUBMITTED, ApprovalState.REJECTED)
                .transition(ApprovalState.APPROVED, ApprovalState.PUBLISHED)
                .terminal(ApprovalState.REJECTED, ApprovalState.PUBLISHED)
                .build();

        // Track state changes
        var history = new ArrayList<String>();
        sm.onEnter(ApprovalState.SUBMITTED, () -> history.add("entered:SUBMITTED"));
        sm.onEnter(ApprovalState.APPROVED, () -> history.add("entered:APPROVED"));
        sm.onEnter(ApprovalState.PUBLISHED, () -> history.add("entered:PUBLISHED"));

        // Submit
        assertThat(sm.transition(ApprovalState.DRAFT, ApprovalState.SUBMITTED)).isTrue();
        assertThat(sm.currentState()).isEqualTo(ApprovalState.SUBMITTED);

        // Try to approve with only 1 approver — guard rejects
        assertThat(sm.transition(ApprovalState.SUBMITTED, ApprovalState.APPROVED, 1)).isFalse();
        assertThat(sm.currentState()).isEqualTo(ApprovalState.SUBMITTED);

        // Approve with 2 approvers — guard passes
        assertThat(sm.transition(ApprovalState.SUBMITTED, ApprovalState.APPROVED, 2)).isTrue();
        assertThat(sm.currentState()).isEqualTo(ApprovalState.APPROVED);

        // Publish
        assertThat(sm.transition(ApprovalState.APPROVED, ApprovalState.PUBLISHED)).isTrue();
        assertThat(sm.currentState()).isEqualTo(ApprovalState.PUBLISHED);

        assertThat(history).containsExactly(
                "entered:SUBMITTED", "entered:APPROVED", "entered:PUBLISHED");
    }

    @Test
    void approvalWorkflow_rejectionPath() {
        var sm = DefaultOrcStateMachine.builder("approval", ApprovalState.class, ApprovalState.DRAFT)
                .transition(ApprovalState.DRAFT, ApprovalState.SUBMITTED)
                .transition(ApprovalState.SUBMITTED, ApprovalState.APPROVED)
                .transition(ApprovalState.SUBMITTED, ApprovalState.REJECTED)
                .terminal(ApprovalState.REJECTED, ApprovalState.PUBLISHED)
                .build();

        sm.transition(ApprovalState.DRAFT, ApprovalState.SUBMITTED);
        sm.transition(ApprovalState.SUBMITTED, ApprovalState.REJECTED);
        assertThat(sm.currentState()).isEqualTo(ApprovalState.REJECTED);
    }
}
