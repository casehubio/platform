package io.casehub.platform.agent.service.generator;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.Test;

import javax.tools.JavaFileObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

class AgentServiceProcessorTest {

    private static String readGenerated(Compilation compilation, String qualifiedName) throws IOException {
        var file = compilation.generatedSourceFile(qualifiedName);
        assertThat(file).as("generated file " + qualifiedName).isPresent();
        return file.get().getCharContent(true).toString();
    }

    @Test
    void generatesProxyForStringReturnMethod() throws IOException {
        JavaFileObject source = JavaFileObjects.forSourceString("test.Greeter",
            """
            package test;
            import io.casehub.platform.agent.AgentService;
            import io.casehub.platform.agent.Query;
            import io.casehub.platform.agent.Param;

            @AgentService(model = "fast", descriptor = "coach")
            public interface Greeter {
                @Query("Say hello to ${name}")
                String greet(@Param("name") String name);
            }
            """);

        Compilation compilation = Compiler.javac()
            .withProcessors(new AgentServiceProcessor())
            .compile(source);

        assertThat(compilation).succeeded();
        String proxy = readGenerated(compilation, "test.GreeterProxy");
        assertThat(proxy).contains("class GreeterProxy implements Greeter");
        assertThat(proxy).contains("invokeBlocking");
        assertThat(proxy).contains("replace(\"${name}\"");
    }

    @Test
    void generatedProxyHasCorrectConstructor() throws IOException {
        JavaFileObject source = JavaFileObjects.forSourceString("test.SimpleAgent",
            """
            package test;
            import io.casehub.platform.agent.AgentService;
            import io.casehub.platform.agent.Query;

            @AgentService(model = "fast")
            public interface SimpleAgent {
                @Query("Do something")
                String doIt();
            }
            """);

        Compilation compilation = Compiler.javac()
            .withProcessors(new AgentServiceProcessor())
            .compile(source);

        assertThat(compilation).succeeded();
        String proxy = readGenerated(compilation, "test.SimpleAgentProxy");
        assertThat(proxy).contains("AgentProvider agentProvider");
        assertThat(proxy).contains("AgentResolver agentResolver");
        assertThat(proxy).contains("implements SimpleAgent");
    }

    @Test
    void generatesProxyWithMultipleMethods() throws IOException {
        JavaFileObject source = JavaFileObjects.forSourceString("test.MultiMethod",
            """
            package test;
            import io.casehub.platform.agent.AgentService;
            import io.casehub.platform.agent.Query;
            import io.casehub.platform.agent.Param;

            @AgentService(model = "fast")
            public interface MultiMethod {
                @Query("Greet ${name}")
                String greet(@Param("name") String name);

                @Query("Summarize ${text}")
                String summarize(@Param("text") String text);
            }
            """);

        Compilation compilation = Compiler.javac()
            .withProcessors(new AgentServiceProcessor())
            .compile(source);

        assertThat(compilation).succeeded();
        String proxy = readGenerated(compilation, "test.MultiMethodProxy");
        assertThat(proxy).contains("greet(java.lang.String name)");
        assertThat(proxy).contains("summarize(java.lang.String text)");
    }

    @Test
    void generatesProxyForStreamingMethod() throws IOException {
        JavaFileObject source = JavaFileObjects.forSourceString("test.StreamAgent",
                                                                """
                                                                package test;
                                                                import io.casehub.platform.agent.AgentService;
                                                                import io.casehub.platform.agent.AgentEvent;
                                                                import io.casehub.platform.agent.Query;
                                                                import io.smallrye.mutiny.Multi;
                                                                
                                                                @AgentService(model = "fast")
                                                                public interface StreamAgent {
                                                                    @Query("Stream response for ${topic}")
                                                                    Multi<AgentEvent> stream(@io.casehub.platform.agent.Param("topic") String topic);
                                                                }
                                                                """);

        Compilation compilation = Compiler.javac()
                                          .withProcessors(new AgentServiceProcessor())
                                          .compile(source);

        assertThat(compilation).succeeded();
        String proxy = readGenerated(compilation, "test.StreamAgentProxy");
        assertThat(proxy).contains("agentProvider.invoke(config)");
        assertThat(proxy).doesNotContain("invokeBlocking");
    }

    @Test
    void generatesProxyForSessionMethod() throws IOException {
        JavaFileObject source = JavaFileObjects.forSourceString("test.SessionAgent",
                                                                """
                                                                package test;
                                                                import io.casehub.platform.agent.AgentService;
                                                                import io.casehub.platform.agent.AgentSession;
                                                                import io.casehub.platform.agent.Query;
                                                                
                                                                @AgentService(model = "fast", descriptor = "coach")
                                                                public interface SessionAgent {
                                                                    @Query("Start session")
                                                                    AgentSession start();
                                                                }
                                                                """);

        Compilation compilation = Compiler.javac()
                                          .withProcessors(new AgentServiceProcessor())
                                          .compile(source);

        assertThat(compilation).succeeded();
        String proxy = readGenerated(compilation, "test.SessionAgentProxy");
        assertThat(proxy).contains("agentProvider.openSession(init)");
        assertThat(proxy).doesNotContain("invokeBlocking");
    }

    @Test
    void generatesProxyForMixedInterface() throws IOException {
        JavaFileObject source = JavaFileObjects.forSourceString("test.MixedAgent",
                                                                """
                                                                package test;
                                                                import io.casehub.platform.agent.AgentService;
                                                                import io.casehub.platform.agent.AgentEvent;
                                                                import io.casehub.platform.agent.AgentSession;
                                                                import io.casehub.platform.agent.Query;
                                                                import io.smallrye.mutiny.Multi;
                                                                
                                                                @AgentService(model = "fast")
                                                                public interface MixedAgent {
                                                                    @Query("Ask something")
                                                                    String ask();
                                                                
                                                                    @Query("Stream it")
                                                                    Multi<AgentEvent> stream();
                                                                
                                                                    @Query("Open session")
                                                                    AgentSession session();
                                                                }
                                                                """);

        Compilation compilation = Compiler.javac()
                                          .withProcessors(new AgentServiceProcessor())
                                          .compile(source);

        assertThat(compilation).succeeded();
        String proxy = readGenerated(compilation, "test.MixedAgentProxy");
        assertThat(proxy).contains("invokeBlocking");
        assertThat(proxy).contains("agentProvider.invoke(config)");
        assertThat(proxy).contains("agentProvider.openSession(init)");
    }
}
