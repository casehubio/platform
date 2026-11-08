package io.casehub.platform.simulation;

public interface CorpusSelector {


    default String type() {return "unknown";}

    String activePhase();

    default void onResolved() {}

    default void reset()      {}

}
