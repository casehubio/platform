package io.casehub.platform.api.acl;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import org.junit.jupiter.api.Test;

class WorkerPermissionRequestTest {

    record TestContext(String definitionId) implements WorkerAuthorizationContext {}

    @Test
    void constructsWithMarkerInterfaceContext() {
        var ctx = new TestContext("my-def");
        var req = new WorkerPermissionRequest("actor", "case",
            Set.of(new WorkerAction("READ", AclAction.READ)), ctx, "tenant");
        assertInstanceOf(TestContext.class, req.context());
        assertEquals("my-def", ((TestContext) req.context()).definitionId());
    }
}
