package io.casehub.yaml.core.orchestration;

public enum Priority {
    BACKGROUND(0), NORMAL(1), HIGH(2);

    private final int level;

    Priority(int level) { this.level = level; }

    public int level() { return level; }
}
