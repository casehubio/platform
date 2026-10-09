package io.casehub.platform.confirmation.generator.test;

import io.casehub.platform.api.confirmation.RequiresConfirmation;

public interface TestSensitiveSpi {
    @RequiresConfirmation(summary = "Process ${amount} ${currency}")
    String process(String amount, String currency);
}
