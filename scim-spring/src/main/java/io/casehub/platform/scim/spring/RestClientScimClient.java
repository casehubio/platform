package io.casehub.platform.scim.spring;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.platform.scim.ScimClient;
import io.casehub.platform.scim.model.ScimGroupResource;
import io.casehub.platform.scim.model.ScimListResponse;
import org.springframework.web.client.RestClient;

public class RestClientScimClient implements ScimClient {

    private static final TypeReference<ScimListResponse<ScimGroupResource>> LIST_TYPE = new TypeReference<>() {};

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public RestClientScimClient(RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public ScimListResponse<ScimGroupResource> listGroups(String filter, String attributes) {
        String json = restClient.get()
                .uri("/Groups?filter={filter}&attributes={attributes}", filter, attributes)
                .retrieve()
                .body(String.class);
        return deserialize(json, LIST_TYPE);
    }

    @Override
    public ScimGroupResource getGroup(String id, String attributes) {
        return restClient.get()
                .uri("/Groups/{id}?attributes={attributes}", id, attributes)
                .retrieve()
                .body(ScimGroupResource.class);
    }

    @Override
    public ScimGroupResource getGroup(String id, String attributes, int startIndex, int count) {
        return restClient.get()
                .uri("/Groups/{id}?attributes={attributes}&startIndex={start}&count={count}",
                        id, attributes, startIndex, count)
                .retrieve()
                .body(ScimGroupResource.class);
    }

    private <T> T deserialize(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize SCIM response", e);
        }
    }
}
