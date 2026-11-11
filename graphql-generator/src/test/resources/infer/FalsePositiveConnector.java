package infer;

import java.util.List;

public class FalsePositiveConnector {

    private FakePlatformService service;

    public FakeResult varTypedSpi(String platform, String id) {
        var p = service.platform(platform);
        return p.capability(userId()).targetMethod(id);
    }

    public FakeResult multipleReturns(String platform, String id) {
        FakeSpiType p = service.platform(platform);
        if (id == null) {
            return p.capability(userId()).defaultMethod();
        }
        return p.capability(userId()).targetMethod(id);
    }

    public List<FakeResult> lambdaReturn(String platform) {
        FakeSpiType p = service.platform(platform);
        List<String> items = List.of("a", "b");
        items.stream().filter(a -> { return a.length() > 1; }).toList();
        return p.listAll();
    }

    public FakeResult noSpiVariable(String query) {
        return new FakeResult();
    }

    private String userId() { return "user"; }
}
