package io.casehub.platform.scim;

import io.casehub.platform.scim.model.ScimGroupResource;
import io.casehub.platform.scim.model.ScimListResponse;

public interface ScimClient {

    ScimListResponse<ScimGroupResource> listGroups(String filter, String attributes);

    ScimGroupResource getGroup(String id, String attributes);

    ScimGroupResource getGroup(String id, String attributes, int startIndex, int count);
}
