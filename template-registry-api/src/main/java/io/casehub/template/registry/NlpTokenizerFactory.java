package io.casehub.template.registry;

import java.util.Set;

public interface NlpTokenizerFactory {

    NlpTokenizer create(String languageTag);

    Set<String> supportedLanguages();
}
