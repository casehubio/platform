package io.casehub.yaml.core.module;

public record ExpansionOptions(SectionDeserializer deserializer,
                               SectionContentRewriter rewriter) {

    public static final ExpansionOptions NONE = new ExpansionOptions(null, null);

    public static ExpansionOptions of(SectionDeserializer deserializer,
                                      SectionContentRewriter rewriter) {
        return new ExpansionOptions(deserializer, rewriter);
    }
}
