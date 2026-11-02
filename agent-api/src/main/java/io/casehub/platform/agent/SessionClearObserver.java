package io.casehub.platform.agent;

@FunctionalInterface
public interface SessionClearObserver {

    void onClearAttempt(String sessionId, int estimatedTokens, boolean actuallyCleared);
}
