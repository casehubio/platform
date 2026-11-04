package io.casehub.yaml.core.annotation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AnnotationParserTest {

    @Test
    void parseSimpleNoArgs() {
        var ann = AnnotationParser.parse("#@background");
        assertEquals("background", ann.name());
        assertTrue(ann.args().isEmpty());
    }

    @Test
    void parseWithLeadingWhitespace() {
        var ann = AnnotationParser.parse("  #@timeout(5s)");
        assertEquals("timeout", ann.name());
        assertEquals("5s", ann.stringArg(0));
    }

    @Test
    void parseSinglePositionalArg() {
        var ann = AnnotationParser.parse("#@timeout(5s)");
        assertEquals("timeout", ann.name());
        assertEquals(1, ann.args().size());
        assertEquals("5s", ann.stringArg(0));
    }

    @Test
    void parseMultiplePositionalArgs() {
        var ann = AnnotationParser.parse("#@requires(agent, rest-client)");
        assertEquals("requires", ann.name());
        assertEquals(2, ann.args().size());
        assertEquals("agent", ann.stringArg(0));
        assertEquals("rest-client", ann.stringArg(1));
    }

    @Test
    void parseMixedArgs() {
        var ann = AnnotationParser.parse("#@retry(3, backoff: exponential)");
        assertEquals("retry", ann.name());
        assertEquals("3", ann.stringArg(0));
        assertEquals("exponential", ann.namedArg("backoff"));
    }

    @Test
    void parseQuotedString() {
        var ann = AnnotationParser.parse("#@description(\"Customer onboarding — triggered on creation\")");
        assertEquals("description", ann.name());
        assertEquals("Customer onboarding — triggered on creation", ann.stringArg(0));
    }

    @Test
    void parseNamedWithQuotedValue() {
        var ann = AnnotationParser.parse("#@schedule(cron: \"0 */6 * * *\")");
        assertEquals("schedule", ann.name());
        assertEquals("0 */6 * * *", ann.namedArg("cron"));
    }

    @Test
    void parseInjectWithQualifier() {
        var ann = AnnotationParser.parse("#@Inject(AgentProvider, qualifier: \"claude\")");
        assertEquals("Inject", ann.name());
        assertEquals("AgentProvider", ann.stringArg(0));
        assertEquals("claude", ann.namedArg("qualifier"));
    }

    @Test
    void parseHyphenatedName() {
        var ann = AnnotationParser.parse("#@on-error(continue)");
        assertEquals("on-error", ann.name());
        assertEquals("continue", ann.stringArg(0));
    }

    @Test
    void parseDuration() {
        var ann = AnnotationParser.parse("#@delay(500ms)");
        assertEquals("delay", ann.name());
        assertEquals("500ms", ann.stringArg(0));
    }

    @Test
    void parseEmptyParens() {
        var ann = AnnotationParser.parse("#@deprecated()");
        assertEquals("deprecated", ann.name());
        assertTrue(ann.args().isEmpty());
    }

    @Test
    void nonAnnotationLineReturnsNull() {
        assertNull(AnnotationParser.parse("# regular comment"));
        assertNull(AnnotationParser.parse("name: value"));
        assertNull(AnnotationParser.parse(""));
    }

    @Test
    void unclosedParenThrows() {
        assertThrows(IllegalArgumentException.class, () -> AnnotationParser.parse("#@timeout(5s"));
    }
}
