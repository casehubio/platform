package io.casehub.platform.api.capacity;

import java.util.List;

public interface ActorCapacityView {

    ActorCapacity getCapacity(String actorId);

    List<ActorCapacity> getOverloaded(double threshold);
}
