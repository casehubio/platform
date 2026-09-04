package io.casehub.platform.api.capacity;

import java.util.List;

public interface CapacitySignalSource {

    String sourceName();

    List<CapacitySignal> signals();
}
