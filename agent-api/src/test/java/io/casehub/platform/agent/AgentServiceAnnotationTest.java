package io.casehub.platform.agent;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AgentServiceAnnotationTest {

    @AgentService(model = "fast", descriptor = "coach",
                  tools = {Object.class}, mcpServers = {"fs"},
                  timeout = 30, simulationEligible = true)
    interface TestService {
        @Query("Do something with ${input}")
        String doSomething(@Param("input") String input);
    }

    @AgentService
    interface DefaultsService {
        @Query("Simple query")
        String simple(String arg);
    }

    @Test
    void annotationAttributesAreAccessible() {
        var ann = TestService.class.getAnnotation(AgentService.class);
        assertNotNull(ann);
        assertEquals("fast", ann.model());
        assertEquals("coach", ann.descriptor());
        assertEquals(30, ann.timeout());
        assertTrue(ann.simulationEligible());
        assertArrayEquals(new Class[]{Object.class}, ann.tools());
        assertArrayEquals(new String[]{"fs"}, ann.mcpServers());
    }

    @Test
    void defaultValuesAreEmpty() {
        var ann = DefaultsService.class.getAnnotation(AgentService.class);
        assertNotNull(ann);
        assertEquals("", ann.model());
        assertEquals("", ann.descriptor());
        assertEquals(0, ann.timeout());
        assertFalse(ann.simulationEligible());
        assertEquals(0, ann.tools().length);
        assertEquals(0, ann.mcpServers().length);
    }

    @Test
    void queryAnnotationHasValue() {
        var method = TestService.class.getMethods()[0];
        var query = method.getAnnotation(Query.class);
        assertNotNull(query);
        assertEquals("Do something with ${input}", query.value());
    }

    @Test
    void paramAnnotationHasValue() throws Exception {
        var method = TestService.class.getMethod("doSomething", String.class);
        var param = method.getParameters()[0].getAnnotation(Param.class);
        assertNotNull(param);
        assertEquals("input", param.value());
    }
}
