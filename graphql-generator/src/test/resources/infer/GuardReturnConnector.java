package infer;

import java.util.List;

public class GuardReturnConnector {

    private FakePlatformService service;

    // Null-guard early return — should still infer the real return
    public FakeResult withNullGuard(String platform, String accountId) {
        FakeSpiType p = service.platform(platform);
        if (p == null) return null;
        return p.capability(userId()).targetMethod(accountId);
    }

    // Empty-list guard return
    public List<FakeResult> withEmptyListGuard(String platform) {
        FakeSpiType p = service.platform(platform);
        if (p == null) return List.of();
        return p.listAll();
    }

    // Genuine ambiguity — two SPI chains in if/else — should still reject
    public FakeResult genuineAmbiguity(String platform, String accountId, boolean flag) {
        FakeSpiType p = service.platform(platform);
        if (flag) {
            return p.capability(userId()).targetMethod(accountId);
        } else {
            return p.capability(userId()).defaultMethod();
        }
    }

    private String userId() { return "user"; }
}
