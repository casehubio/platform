package io.casehub.platform.registration;

public interface BreachDetector {
    boolean isBreached(String password);
}
