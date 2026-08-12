package io.casehub.platform.acl;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.casehub.platform.api.acl.AclAction;
import io.casehub.platform.api.acl.AuthorizationDecision;
import io.casehub.platform.api.acl.WorkerAction;
import io.casehub.platform.api.acl.WorkerPermissionRequest;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AutoApproveWorkerAuthorizationPolicyTest {

    private static final WorkerAction READ_CONTEXT = new WorkerAction("READ_CONTEXT", AclAction.READ);
    private static final WorkerAction WRITE_CONTEXT = new WorkerAction("WRITE_CONTEXT", AclAction.WRITE);
    private static final WorkerAction CLAIM_WORK_ITEM = new WorkerAction("CLAIM_WORK_ITEM", AclAction.CLAIM);
    private static final WorkerAction ADMIN = new WorkerAction("ADMIN", AclAction.ADMIN);

    private final AutoApproveWorkerAuthorizationPolicy policy = new AutoApproveWorkerAuthorizationPolicy();

    @Test
    void evaluate_approvesReadContext() {
        var request = new WorkerPermissionRequest(
            "agent:worker-1", "case", Set.of(READ_CONTEXT), null, "tenant-1");
        AuthorizationDecision decision = policy.evaluate(request);
        assertTrue(decision.approved());
    }

    @Test
    void evaluate_approvesAdmin() {
        var request = new WorkerPermissionRequest(
            "agent:pool-1", "case", Set.of(ADMIN), null, "tenant-1");
        AuthorizationDecision decision = policy.evaluate(request);
        assertTrue(decision.approved());
    }

    @Test
    void evaluate_approvesMultipleActions() {
        var request = new WorkerPermissionRequest(
            "agent:worker-1", "case",
            Set.of(READ_CONTEXT, WRITE_CONTEXT, CLAIM_WORK_ITEM),
            null, "tenant-1");
        AuthorizationDecision decision = policy.evaluate(request);
        assertTrue(decision.approved());
    }

    @Test
    void evaluate_approvesEphemeralActor() {
        var request = new WorkerPermissionRequest(
            "agent:worker-abcd1234-ef56", "case",
            Set.of(READ_CONTEXT), null, "tenant-1");
        AuthorizationDecision decision = policy.evaluate(request);
        assertTrue(decision.approved());
    }
}
