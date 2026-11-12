package infer;

import java.util.List;

public class StandardConnector {

    private FakePlatformService service;

    // Pattern A — standard routing with capability accessor
    public FakeResult simpleQuery(String platform, String accountId) {
        FakeSpiType p = service.platform(platform);
        return p.capability(userId()).targetMethod(accountId);
    }

    // Pattern A — with requireCapability call
    public FakeResult withRequireCapability(String platform, String accountId) {
        FakeSpiType p = service.platform(platform);
        requireCapability(p, FakeCapability.class, "targetMethod");
        return p.capability(userId()).targetMethod(accountId);
    }

    // Pattern A — no capability routing (direct call on SPI)
    public List<FakeResult> noCapabilityRouting(String platform) {
        FakeSpiType p = service.platform(platform);
        return p.listAll();
    }

    // Pattern A — multiple accessor arguments
    public FakeResult multipleAccessorArgs(String platform, String accountId,
                                            String userId) {
        FakeSpiType p = service.platform(platform);
        return p.capability(userId, "extra").targetMethod(accountId);
    }

    // Pattern B — voice-style helper-method routing
    public FakeResult helperMethodRouting(String voice, String text) {
        FakeResolved resolved = resolveVoice(voice);
        FakeSpiType platform = resolved.platform();
        requireCapability(platform, FakeCapability.class, "targetMethod");
        return platform.capability(userId()).targetMethod(text);
    }

    private String userId() { return "user"; }
    private static void requireCapability(Object p, Class<?> cap, String op) {}
    private FakeResolved resolveVoice(String voice) { return null; }
}

class FakePlatformService {
    FakeSpiType platform(String name) { return null; }
    boolean supports(String name) { return true; }
}

class FakeResolved {
    FakeSpiType platform() { return null; }
}

class FakeSpiType {
    FakeCapability capability(String... args) { return null; }
    java.util.List<FakeResult> listAll() { return null; }
    void removeItem(String id) {}
}

class FakeCapability {
    FakeResult targetMethod(String arg) { return null; }
    FakeResult defaultMethod() { return null; }
    FakeResult listItems(String id, int size) { return null; }
    FakeResult export(String fmt) { return null; }
    void deleteItem(String id) {}
}

class FakeResult {}
