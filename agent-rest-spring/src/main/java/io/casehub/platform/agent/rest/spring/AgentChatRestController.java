package io.casehub.platform.agent.rest.spring;

import io.casehub.platform.agent.AgentChatApi;
import io.casehub.platform.agent.AgentChatRequest;
import io.casehub.platform.agent.AgentChatResponse;
import io.casehub.platform.agent.AgentDescriptor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(value = "/api/agent-chat", produces = MediaType.APPLICATION_JSON_VALUE)
public class AgentChatRestController {

    private final AgentChatApi agentChatApi;

    public AgentChatRestController(AgentChatApi agentChatApi) {
        this.agentChatApi = agentChatApi;
    }

    @GetMapping("/list-agents")
    public ResponseEntity<List<AgentDescriptor>> listAgents() {
        return ResponseEntity.ok(agentChatApi.listAgents());
    }

    @PostMapping(value = "/invoke", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AgentChatResponse> invoke(@RequestBody AgentChatRequest request) {
        return ResponseEntity.ok(agentChatApi.invoke(request));
    }
}
