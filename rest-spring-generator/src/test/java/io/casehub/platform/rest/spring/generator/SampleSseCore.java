package io.casehub.platform.rest.spring.generator;

import java.util.concurrent.Flow;

public class SampleSseCore {
    public Flow.Publisher<String> streamEvents() { return subscriber -> {}; }
}
