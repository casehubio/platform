package infer;

import java.util.List;

public class DefaultValueConnector {

    private FakePlatformService service;

    public FakeResult withStaticIntDefault(String platform, String accountId,
                                            Integer pageSize) {
        FakeSpiType p = service.platform(platform);
        int size = pageSize != null ? pageSize : 50;
        return p.capability(userId()).listItems(accountId, size);
    }

    public FakeResult withStaticStringDefault(String platform, String format) {
        FakeSpiType p = service.platform(platform);
        String fmt = format != null ? format : "json";
        return p.capability(userId()).export(fmt);
    }

    private String userId() { return "user"; }
}
