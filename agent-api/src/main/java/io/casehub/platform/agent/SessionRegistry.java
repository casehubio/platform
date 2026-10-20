package io.casehub.platform.agent;

import java.time.Duration;

public interface SessionRegistry {

    AgentSession openSession(String id, AgentSessionInit init);

    AgentSession openSession(String id, AgentSessionInit init, Duration timeout);

    void releaseSession(String id);

    int sessionCount();
}
