package io.casehub.yaml.jackson;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(builder = DeclarationFileBuilder.class)
abstract class DeclarationFileMixin {}
