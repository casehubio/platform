package io.casehub.platform.oidc;

import io.casehub.platform.api.identity.MissingTenancyException;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.assertj.core.api.Assertions.*;

class MissingTenancyExceptionMapperTest {

    private final MissingTenancyExceptionMapper mapper = new MissingTenancyExceptionMapper();

    @Test
    void toResponse_returns403() {
        Response response = mapper.toResponse(new MissingTenancyException("alice"));
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void toResponse_bodyContainsErrorField() {
        Response response = mapper.toResponse(new MissingTenancyException("alice"));
        JsonObject body = Json.createReader(new StringReader((String) response.getEntity())).readObject();
        assertThat(body.getString("error")).isEqualTo("missing_tenancy");
    }

    @Test
    void toResponse_bodyContainsActorId() {
        Response response = mapper.toResponse(new MissingTenancyException("bob"));
        JsonObject body = Json.createReader(new StringReader((String) response.getEntity())).readObject();
        assertThat(body.getString("actorId")).isEqualTo("bob");
    }

    @Test
    void toResponse_bodyContainsMessage() {
        Response response = mapper.toResponse(new MissingTenancyException("alice"));
        JsonObject body = Json.createReader(new StringReader((String) response.getEntity())).readObject();
        assertThat(body.getString("message")).isEqualTo("JWT does not contain a tenancyId claim");
    }

    @Test
    void toResponse_contentTypeIsJson() {
        Response response = mapper.toResponse(new MissingTenancyException("alice"));
        assertThat(response.getMediaType().toString()).isEqualTo("application/json");
    }
}
