package io.casehub.template.registry.mf2;

import io.casehub.template.registry.MessageFormatSchema;
import io.casehub.template.registry.SelectorInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class Mf2SchemaExtractor {

    private static final Pattern MATCH_SELECTOR = Pattern.compile(
        "\\.input\\s+\\{\\$([a-zA-Z_][a-zA-Z0-9_]*)\\s+:([a-zA-Z]+)");
    private static final Pattern VARIANT_KEY = Pattern.compile(
        "(?m)^\\s*([^{\\s]+)\\s+\\{\\{");

    private Mf2SchemaExtractor() {}

    static MessageFormatSchema extract(String pattern) {
        List<SelectorInfo> selectors = new ArrayList<>();
        Matcher matcherSel = MATCH_SELECTOR.matcher(pattern);
        while (matcherSel.find()) {
            selectors.add(new SelectorInfo(matcherSel.group(1), matcherSel.group(2)));
        }

        List<String> variantKeys = new ArrayList<>();
        Matcher matcherVar = VARIANT_KEY.matcher(pattern);
        while (matcherVar.find()) {
            String key = matcherVar.group(1);
            if (!key.startsWith(".")) {
                variantKeys.add(key);
            }
        }

        return new MessageFormatSchema(pattern, selectors, variantKeys);
    }
}
