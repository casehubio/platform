package io.casehub.schema.generator.module;

import com.github.victools.jsonschema.generator.CustomDefinition;
import com.github.victools.jsonschema.generator.Module;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;

public class EnumInliningModule implements Module {

    @Override
    public void applyToConfigBuilder(SchemaGeneratorConfigBuilder builder) {
        builder.forTypesInGeneral()
               .withCustomDefinitionProvider((javaType, context) -> {
                   if (javaType.isInstanceOf(Enum.class)) {
                       com.fasterxml.jackson.databind.node.ObjectNode enumNode =
                               context.getGeneratorConfig().createObjectNode();
                       enumNode.put("type", "string");
                       com.fasterxml.jackson.databind.node.ArrayNode values =
                               enumNode.putArray("enum");
                       for (Object constant : javaType.getErasedType().getEnumConstants()) {
                           values.add(constant.toString());
                       }
                       return new CustomDefinition(enumNode,
                                                   CustomDefinition.DefinitionType.INLINE,
                                                   CustomDefinition.AttributeInclusion.YES);
                   }
                   return null;
               });}
}
