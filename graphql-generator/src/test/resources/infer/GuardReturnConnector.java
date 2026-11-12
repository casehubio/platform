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

    // Pre-SPI guard return — supports() check before .platform() call
    public FakeResult withPreSpiGuard(String platform, String text) {
        if (!service.supports(platform)) return null;
        FakeSpiType p = service.platform(platform);
        return p.capability(userId()).targetMethod(text);
    }

    // Try/catch — success return in try, error return in catch
    public FakeResult withTryCatch(String platform, String accountId) {
        FakeSpiType p = service.platform(platform);
        try {
            return p.capability(userId()).targetMethod(accountId);
        } catch (Exception e) {
            return null;
        }
    }

    // Three returns — pre-SPI guard + try/catch
    public FakeResult withThreeReturns(String platform, String text) {
        if (!service.supports(platform)) return null;
        FakeSpiType p = service.platform(platform);
        try {
            return p.capability(userId()).targetMethod(text);
        } catch (Exception e) {
            return null;
        }
    }

    private String userId() { return "user"; }
}
