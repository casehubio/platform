package io.casehub.template.registry;

import java.util.Map;

public interface MessageFormatResolver {

    interface CompiledMessage {}

    CompiledMessage compile(String pattern, String languageTag);

    String format(CompiledMessage compiledMessage, Map<String, String> values);

    MessageFormatSchema extractSchema(String pattern);
}
