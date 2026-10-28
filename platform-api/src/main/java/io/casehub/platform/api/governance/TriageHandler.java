package io.casehub.platform.api.governance;

public interface TriageHandler {
    TriageDecision evaluate(TriageContext context);
}
