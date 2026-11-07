package io.casehub.platform.api.mcp;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Transforms the return value of an MCP tool through another SPI
 * method before the agent sees it. REST and GraphQL return the
 * original type unchanged.
 *
 * <p>The dispatcher invokes the primary method, then passes its
 * return value to the transform SPI method. The transform method
 * must accept the primary return type as its first parameter.
 *
 * <p>When {@link #perElement()} is {@code true} and the primary
 * method returns a {@code List}, the transform is applied to each
 * element individually and the results collected into a new list.
 *
 * <p>Examples:
 * <pre>
 *   // byte[] → CorpusEntry (whole-return transformation)
 *   &#64;McpTransform(spi = MetadataExtractor.class, method = "extract")
 *   byte[] readVersion(String path, int version);
 *
 *   // List&lt;String&gt; → List&lt;EntrySummary&gt; (per-element transformation)
 *   &#64;McpTransform(spi = EntryEnricher.class, method = "summarise", perElement = true)
 *   List&lt;String&gt; listEntries(String prefix);
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface McpTransform {
    Class<?> spi();
    String method();
    boolean perElement() default false;
}
