package io.casehub.platform.governance;

import io.casehub.platform.api.governance.TriageContext;
import io.casehub.platform.api.governance.TriageDecision;
import io.casehub.platform.api.governance.TriageHandler;

public class NoOpTriageHandler implements TriageHandler {

    @Override
    public TriageDecision evaluate(TriageContext context) {
        return new TriageDecision.Abandon("no triage handler configured");
    }
}
