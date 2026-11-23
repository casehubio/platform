package io.casehub.platform.agent.config;

import com.fasterxml.jackson.annotation.JsonAlias;

abstract class ModelDescriptorMixin {
    @JsonAlias("instanceId")
    abstract String backendInstanceId();
}
