package io.casehub.platform.simulation;

public interface CorpusSelector {

    String activePhase();

    default void onResolved() {}
}
