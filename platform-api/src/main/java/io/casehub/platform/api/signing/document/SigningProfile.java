package io.casehub.platform.api.signing.document;

public enum SigningProfile {
    B_B, B_T, B_LT, B_LTA;

    public boolean requiresTimestamp() {
        return this != B_B;
    }
}
