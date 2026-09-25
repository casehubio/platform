package io.casehub.yaml.jackson;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(builder = StepDefinitionFileBuilder.class)
abstract class StepDefinitionFileMixin {}
