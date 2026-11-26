package io.casehub.platform.agent.service;

import io.casehub.platform.agent.AgentService;
import io.casehub.platform.agent.Param;
import io.casehub.platform.agent.Query;

@AgentService(model = "fast", descriptor = "coach")
public interface TestCoach {
    @Query("Break ${goal} into tasks")
    String decompose(@Param("goal") String goal);
}
