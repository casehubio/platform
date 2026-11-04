package io.casehub.yaml.core.annotation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnnotationExtractorTest {

    @Test
    void extractDocumentLevelAnnotations() {
        String yaml = """
                #@description("Customer onboarding")
                #@requires(agent, rest-client)
                name: Customer onboarding
                steps:
                  - rest-request:
                      url: /api
                """;
        var map = AnnotationExtractor.extract(yaml);
        assertEquals(2, map.documentAnnotations().size());
        assertTrue(map.forDocument("description").isPresent());
        assertTrue(map.forDocument("requires").isPresent());
    }

    @Test
    void extractStepAnnotations() {
        String yaml = """
                name: Test
                steps:
                  #@timeout(5s)
                  - rest-request:
                      url: /api
                  #@retry(3)
                  - agent-invoke:
                      prompt: hello
                """;
        var map = AnnotationExtractor.extract(yaml);
        assertTrue(map.documentAnnotations().isEmpty());
        assertEquals(1, map.forStep(0).size());
        assertEquals("timeout", map.forStep(0).get(0).name());
        assertEquals(1, map.forStep(1).size());
        assertEquals("retry", map.forStep(1).get(0).name());
    }

    @Test
    void extractMultipleAnnotationsOnSameStep() {
        String yaml = """
                name: Test
                steps:
                  #@timeout(2s)
                  #@audit("PII access")
                  - rest-request:
                      url: /api
                """;
        var map = AnnotationExtractor.extract(yaml);
        assertEquals(2, map.forStep(0).size());
        assertTrue(map.hasStepAnnotation(0, "timeout"));
        assertTrue(map.hasStepAnnotation(0, "audit"));
    }

    @Test
    void extractInjectAnnotations() {
        String yaml = """
                #@Inject(AgentProvider, qualifier: "claude")
                claude: agent
                #@Inject(CaseMemoryStore)
                memory: store
                name: Test
                steps:
                  - rest-request:
                      url: /api
                """;
        var map = AnnotationExtractor.extract(yaml);
        var injects = map.documentAnnotations().stream()
            .filter(a -> a.name().equals("Inject")).toList();
        assertEquals(2, injects.size());
        assertEquals("AgentProvider", injects.get(0).stringArg(0));
        assertEquals("claude", injects.get(0).namedArg("qualifier"));
        assertEquals("CaseMemoryStore", injects.get(1).stringArg(0));
    }

    @Test
    void duplicateAnnotationOnSameTargetThrows() {
        String yaml = """
                name: Test
                steps:
                  #@timeout(5s)
                  #@timeout(3s)
                  - rest-request:
                      url: /api
                """;
        assertThrows(IllegalArgumentException.class, () -> AnnotationExtractor.extract(yaml));
    }

    @Test
    void noAnnotationsReturnsEmpty() {
        String yaml = """
                name: Test
                steps:
                  - rest-request:
                      url: /api
                """;
        var map = AnnotationExtractor.extract(yaml);
        assertTrue(map.documentAnnotations().isEmpty());
        assertTrue(map.forStep(0).isEmpty());
    }

    @Test
    void mixedDocumentAndStepAnnotations() {
        String yaml = """
                #@version(2.1)
                name: Test
                steps:
                  #@timeout(5s)
                  - rest-request:
                      url: /api
                """;
        var map = AnnotationExtractor.extract(yaml);
        assertEquals(1, map.documentAnnotations().size());
        assertEquals("version", map.documentAnnotations().get(0).name());
        assertEquals(1, map.forStep(0).size());
        assertEquals("timeout", map.forStep(0).get(0).name());
    }

    @Test
    void regularCommentsIgnored() {
        String yaml = """
                # This is a regular comment
                #@description("Test")
                name: Test
                steps:
                  # another comment
                  #@timeout(5s)
                  - rest-request:
                      url: /api
                """;
        var map = AnnotationExtractor.extract(yaml);
        assertEquals(1, map.documentAnnotations().size());
        assertEquals(1, map.forStep(0).size());
    }

    @Test
    void nullInputReturnsEmpty() {
        assertEquals(AnnotationMap.empty(), AnnotationExtractor.extract(null));
        assertEquals(AnnotationMap.empty(), AnnotationExtractor.extract(""));
    }

    @Test
    void stepsWithNoAnnotationsGetCorrectIndices() {
        String yaml = """
                name: Test
                steps:
                  - first-step:
                      url: /api
                  #@timeout(5s)
                  - second-step:
                      url: /api
                  - third-step:
                      url: /api
                """;
        var map = AnnotationExtractor.extract(yaml);
        assertTrue(map.forStep(0).isEmpty());
        assertEquals(1, map.forStep(1).size());
        assertEquals("timeout", map.forStep(1).get(0).name());
        assertTrue(map.forStep(2).isEmpty());
    }

    @Test
    void extractAnnotationsInsideBlock() {
        String yaml = """
                      name: Test
                      steps:
                        - block:
                            #@timeout(5s)
                            - rest-request:
                                url: /api
                            #@retry(3)
                            - agent-invoke:
                                prompt: hello
                      """;
        var map = AnnotationExtractor.extract(yaml);
        assertEquals(1, map.forStep("0.0").size());
        assertEquals("timeout", map.forStep("0.0").get(0).name());
        assertEquals(1, map.forStep("0.1").size());
        assertEquals("retry", map.forStep("0.1").get(0).name());
    }

    @Test
    void mixedTopLevelAndNestedAnnotations() {
        String yaml = """
                      name: Test
                      steps:
                        #@timeout(10s)
                        - block:
                            #@retry(3)
                            - rest-request:
                                url: /api
                        #@audit
                        - rest-request:
                            url: /api2
                      """;
        var map = AnnotationExtractor.extract(yaml);
        assertEquals(1, map.forStep("0").size());
        assertEquals("timeout", map.forStep("0").get(0).name());
        assertEquals(1, map.forStep("0.0").size());
        assertEquals("retry", map.forStep("0.0").get(0).name());
        assertEquals(1, map.forStep("1").size());
        assertEquals("audit", map.forStep("1").get(0).name());
    }

    @Test
    void deeplyNestedAnnotations() {
        String yaml = """
                      name: Test
                      steps:
                        - block:
                            - block:
                                #@timeout(5s)
                                - rest-request:
                                    url: /api
                      """;
        var map = AnnotationExtractor.extract(yaml);
        assertEquals(1, map.forStep("0.0.0").size());
        assertEquals("timeout", map.forStep("0.0.0").get(0).name());
    }

    @Test
    void annotationsAcrossTryCatch() {
        String yaml = """
                      name: Test
                      steps:
                        - try:
                            #@timeout(5s)
                            - rest-request:
                                url: /api
                          catch:
                            #@retry(3)
                            - agent-invoke:
                                prompt: handle error
                      """;
        var map = AnnotationExtractor.extract(yaml);
        assertEquals(1, map.forStep("0.0").size());
        assertEquals("timeout", map.forStep("0.0").get(0).name());
        assertEquals(1, map.forStep("0.1").size());
        assertEquals("retry", map.forStep("0.1").get(0).name());
    }

    @Test
    void nestedStepCounterResetsForNewParent() {
        String yaml = """
                      name: Test
                      steps:
                        - block:
                            #@timeout(5s)
                            - rest-request:
                                url: /api
                            - agent-invoke:
                                prompt: hello
                        - block:
                            #@retry(3)
                            - rest-request:
                                url: /api2
                      """;
        var map = AnnotationExtractor.extract(yaml);
        assertEquals(1, map.forStep("0.0").size());
        assertEquals("timeout", map.forStep("0.0").get(0).name());
        assertTrue(map.forStep("0.1").isEmpty());
        assertEquals(1, map.forStep("1.0").size());
        assertEquals("retry", map.forStep("1.0").get(0).name());
    }


}
