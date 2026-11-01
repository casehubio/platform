package io.casehub.platform.registration.core;

import io.casehub.platform.registration.BreachDetector;

public class NoOpBreachDetector implements BreachDetector {
    @Override
    public boolean isBreached(String password) {
        return false;
    }
}
