package infer;

public class VoidConnector {

    private FakePlatformService service;

    public void deleteItem(String platform, String itemId) {
        FakeSpiType p = service.platform(platform);
        requireCapability(p, FakeCapability.class, "deleteItem");
        p.capability(userId()).deleteItem(itemId);
    }

    public void directVoidCall(String platform, String itemId) {
        FakeSpiType p = service.platform(platform);
        p.removeItem(itemId);
    }

    private String userId() { return "user"; }
    private static void requireCapability(Object p, Class<?> cap, String op) {}
}
