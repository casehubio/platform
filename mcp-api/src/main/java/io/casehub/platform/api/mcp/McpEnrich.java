package io.casehub.platform.api.mcp;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Enriches the MCP tool result by calling a second SPI method and
 * merging its result into the response. REST and GraphQL projections
 * return the original type unchanged.
 *
 * <p>The enrichment method is invoked with parameters matched by name
 * from the primary method's parameter list. The enrichment result is
 * added to the MCP response under the field name specified by {@link #as()}.
 *
 * <p>Example:
 * <pre>
 *   &#64;McpEnrich(spi = CorpusReader.class, method = "versions", as = "versions")
 *   CorpusEntry getEntry(String corpusName, String entryId);
 * </pre>
 * The MCP tool returns: {@code {id, body, metadata, ..., versions: [...]}}
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(McpEnrichments.class)
public @interface McpEnrich {
    Class<?> spi();
    String method();
    String as();
}
