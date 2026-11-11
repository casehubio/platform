package infer;

public class ContactsConnector {

    private ContactsPlatformService platformService;
    private Object identity;

    public FakeResult listContacts(String platformId, String cursor, Integer pageSize) {
        var p = platformService.platform(platformId);
        requireCapability(p, ContactsCapability.class, "listContacts");
        int size = pageSize != null ? pageSize : 20;
        return p.contactRead(userId()).list(cursor, size);
    }

    public FakeResult getContact(String platformId, String contactId) {
        var p = platformService.platform(platformId);
        requireCapability(p, ContactsCapability.class, "getContact");
        return p.contactRead(userId()).get(contactId);
    }

    private String userId() { return "user"; }
    private static void requireCapability(Object p, Class<?> cap, String op) {}
}

class ContactsPlatformService {
    ContactsPlatform platform(String id) { return null; }
}

class ContactsPlatform {
    ContactsCapability contactRead(String userId) { return null; }
}

class ContactsCapability {
    FakeResult list(String cursor, int size) { return null; }
    FakeResult get(String contactId) { return null; }
}
