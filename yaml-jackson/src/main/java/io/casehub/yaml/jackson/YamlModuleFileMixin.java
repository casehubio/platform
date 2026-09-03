package io.casehub.yaml.jackson;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(builder = YamlModuleFileBuilder.class)
abstract class YamlModuleFileMixin {}
