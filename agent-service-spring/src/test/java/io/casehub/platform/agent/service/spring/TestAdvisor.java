package io.casehub.platform.agent.service.spring;

import io.casehub.platform.agent.AgentService;
import io.casehub.platform.agent.Param;
import io.casehub.platform.agent.Query;

@AgentService(descriptor = "advisor")
public interface TestAdvisor {
    @Query("Advise on ${topic}")
    String advise(@Param("topic") String topic);
}
