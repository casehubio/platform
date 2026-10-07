package io.casehub.platform.spring.integration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;

@SpringBootApplication(excludeName = {
        "org.springframework.ai.mcp.server.common.autoconfigure.McpServerAutoConfiguration",
        "io.casehub.platform.agent.session.spring.AgentSessionAutoConfiguration"
})
@EntityScan("io.casehub.platform")
public class TestApplication {

    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }
}
