package infer;

import java.util.List;

public class VarConnector {

    private FakePlatformService platformService;
    private Object identity;

    public FakeResult varWithReturn(String platformId, String accountId) {
        var p = platformService.platform(platformId);
        requireCapability(p, FakeCapability.class, "targetMethod");
        return p.capability(userId()).targetMethod(accountId);
    }

    public List<FakeResult> varNoCapabilityRouting(String platformId) {
        var p = platformService.platform(platformId);
        return p.listAll();
    }

    public void varVoidMethod(String platformId, String itemId) {
        var p = platformService.platform(platformId);
        requireCapability(p, FakeCapability.class, "deleteItem");
        p.capability(userId()).deleteItem(itemId);
    }

    public FakeResult varWithDefault(String platformId, String accountId,
                                      Integer pageSize) {
        var p = platformService.platform(platformId);
        int size = pageSize != null ? pageSize : 20;
        return p.capability(userId()).targetMethod(accountId);
    }

    private String userId() { return "user"; }
    private static void requireCapability(Object p, Class<?> cap, String op) {}
}
