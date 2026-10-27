package io.casehub.platform.coordination.protocol;

import io.casehub.yaml.core.orchestration.EventRouter;
import io.casehub.yaml.core.orchestration.OrcStateMachine;

import java.util.List;
import java.util.Map;

final class ProtocolInstance {

    private final OrcStateMachine<String> stateMachine;
    private final EventRouter<String> router;
    private final Map<String, ProtocolAdvisory> advisoryMap;

    ProtocolInstance(OrcStateMachine<String> stateMachine,
                    EventRouter<String> router,
                    Map<String, ProtocolAdvisory> advisoryMap) {
        this.stateMachine = stateMachine;
        this.router = router;
        this.advisoryMap = advisoryMap;
    }

    String currentState() {
        return stateMachine.currentState();
    }

    boolean fire(String event, Object context) {
        return router.fire(event, context);
    }

    List<ProtocolAdvisory> advisories() {
        ProtocolAdvisory advisory = advisoryMap.get(currentState());
        return advisory != null ? List.of(advisory) : List.of();
    }

    String serialize() {
        return "{\"current\":\"" + currentState() + "\"}";
    }
}
