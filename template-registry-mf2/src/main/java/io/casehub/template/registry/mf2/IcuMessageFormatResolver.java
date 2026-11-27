package io.casehub.template.registry.mf2;

import com.ibm.icu.message2.MessageFormatter;
import io.casehub.template.registry.MessageFormatResolver;
import io.casehub.template.registry.MessageFormatSchema;

import java.util.IllformedLocaleException;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class IcuMessageFormatResolver implements MessageFormatResolver {

    private record IcuCompiledMessage(
        MessageFormatter formatter,
        String pattern,
        String languageTag
    ) implements CompiledMessage {}

    @Override
    public CompiledMessage compile(String pattern, String languageTag) {
        Objects.requireNonNull(pattern, "pattern");
        Objects.requireNonNull(languageTag, "languageTag");

        Locale locale;
        try {
            locale = new Locale.Builder().setLanguageTag(languageTag).build();
        } catch (IllformedLocaleException e) {
            throw new IllegalArgumentException(
                "Invalid BCP 47 language tag: '" + languageTag + "'", e);
        }

        try {
            MessageFormatter formatter = MessageFormatter.builder()
                .setPattern(pattern)
                .setLocale(locale)
                .build();
            return new IcuCompiledMessage(formatter, pattern, languageTag);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                "Invalid MF2 pattern: " + e.getMessage(), e);
        }
    }

    @Override
    public String format(CompiledMessage compiledMessage,
                          Map<String, String> values) {
        Objects.requireNonNull(compiledMessage, "compiledMessage");
        Objects.requireNonNull(values, "values");

        IcuCompiledMessage compiled = (IcuCompiledMessage) compiledMessage;
        Map<String, Object> typedValues = Mf2TypeCoercer.coerce(values);
        return compiled.formatter().formatToString(typedValues);
    }

    @Override
    public MessageFormatSchema extractSchema(String pattern) {
        Objects.requireNonNull(pattern, "pattern");
        return Mf2SchemaExtractor.extract(pattern);
    }
}
