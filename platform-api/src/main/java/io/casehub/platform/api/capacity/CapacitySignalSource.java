package io.casehub.platform.api.capacity;

import java.util.List;

public interface CapacitySignalSource {

    List<CapacitySignal> observe(String actorId);

    List<CapacitySignal> observeOverloaded(double threshold);
}
