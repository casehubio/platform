package io.casehub.platform.acl;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.casehub.platform.api.acl.AuthorizationDecision;
import io.casehub.platform.api.acl.WorkerAction;
import io.casehub.platform.api.acl.WorkerPermissionRequest;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AutoApproveWorkerAuthorizationPolicyTest {

    private final AutoApproveWorkerAuthorizationPolicy policy = new AutoApproveWorkerAuthorizationPolicy();

    @Test
    void evaluate_approvesReadContext() {
        var request = new WorkerPermissionRequest(
            "agent:worker-1", "case", Set.of(WorkerAction.READ_CONTEXT), "ns/def/v1", "tenant-1");
        AuthorizationDecision decision = policy.evaluate(request);
        assertTrue(decision.approved());
    }

    @Test
    void evaluate_approvesAdmin() {
        var request = new WorkerPermissionRequest(
            "agent:pool-1", "case", Set.of(WorkerAction.ADMIN), "ns/def/v1", "tenant-1");
        AuthorizationDecision decision = policy.evaluate(request);
        assertTrue(decision.approved());
    }

    @Test
    void evaluate_approvesMultipleActions() {
        var request = new WorkerPermissionRequest(
            "agent:worker-1", "case",
            Set.of(WorkerAction.READ_CONTEXT, WorkerAction.WRITE_CONTEXT, WorkerAction.CLAIM_WORK_ITEM),
            "ns/def/v1", "tenant-1");
        AuthorizationDecision decision = policy.evaluate(request);
        assertTrue(decision.approved());
    }

    @Test
    void evaluate_approvesEphemeralActor() {
        var request = new WorkerPermissionRequest(
            "agent:worker-abcd1234-ef56", "case",
            Set.of(WorkerAction.READ_CONTEXT), "ns/def/v1", "tenant-1");
        AuthorizationDecision decision = policy.evaluate(request);
        assertTrue(decision.approved());
    }
}
