package io.casehub.platform.scim.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RestClientScimClientTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void listGroups_deserializes_response() {
        var restClientBuilder = RestClient.builder().baseUrl("http://scim.test");
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();

        String json = """
                {"totalResults":1,"startIndex":1,"itemsPerPage":100,
                 "Resources":[{"id":"g1","displayName":"engineers",
                 "members":[{"value":"u1","display":"Alice"}]}]}""";
        server.expect(requestTo("http://scim.test/Groups?filter=test&attributes=id"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        var client = new RestClientScimClient(restClientBuilder.build(), mapper);
        var result = client.listGroups("test", "id");

        assertThat(result.totalResults()).isEqualTo(1);
        assertThat(result.resources()).hasSize(1);
        assertThat(result.resources().get(0).displayName()).isEqualTo("engineers");
        assertThat(result.resources().get(0).members()).hasSize(1);
        assertThat(result.resources().get(0).members().get(0).value()).isEqualTo("u1");
        server.verify();
    }

    @Test
    void getGroup_returns_resource() {
        var restClientBuilder = RestClient.builder().baseUrl("http://scim.test");
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();

        String json = """
                {"id":"g1","displayName":"engineers",
                 "members":[{"value":"u1","display":"Alice"},{"value":"u2","display":"Bob"}]}""";
        server.expect(requestTo("http://scim.test/Groups/g1?attributes=members"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        var client = new RestClientScimClient(restClientBuilder.build(), mapper);
        var result = client.getGroup("g1", "members");

        assertThat(result.id()).isEqualTo("g1");
        assertThat(result.members()).hasSize(2);
        server.verify();
    }

    @Test
    void getGroup_with_pagination_params() {
        var restClientBuilder = RestClient.builder().baseUrl("http://scim.test");
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();

        String json = """
                {"id":"g1","displayName":"engineers","members":[{"value":"u3","display":"Carol"}]}""";
        server.expect(requestTo("http://scim.test/Groups/g1?attributes=members&startIndex=4&count=3"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        var client = new RestClientScimClient(restClientBuilder.build(), mapper);
        var result = client.getGroup("g1", "members", 4, 3);

        assertThat(result.members()).hasSize(1);
        assertThat(result.members().get(0).value()).isEqualTo("u3");
        server.verify();
    }
}
