package io.casehub.platform.mcp;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class DirectDomainImpl implements DirectDomainApi {

    @Override
    public String lookup(String id) {
        return "found:" + id;
    }

    @Override
    public String createItem(String name, int count) {
        return name + ":" + count;
    }

    @Override
    public String helper() {
        return "not exposed";
    }
}
