package infer;

import infer.spi.*;

public class StarImportConnector {

    private StarPlatformService service;

    public StarResult simpleQuery(String platform, String accountId) {
        StarSpiType p = service.platform(platform);
        return p.capability(userId()).targetMethod(accountId);
    }

    public java.util.List<StarResult> noCapabilityRouting(String platform) {
        StarSpiType p = service.platform(platform);
        return p.listAll();
    }

    private String userId() { return "user"; }
}
