package io.casehub.platform.api.model;

public enum CostTier {
    FREE(0), LOW(1), MEDIUM(2), HIGH(3), PREMIUM(4);

    private final int rank;

    CostTier(int rank) { this.rank = rank; }

    public int rank() { return rank; }
}
