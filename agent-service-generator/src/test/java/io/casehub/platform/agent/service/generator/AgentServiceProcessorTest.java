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
}
