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
    void generatesProxyForPojoReturnType() throws IOException {
        JavaFileObject taskType = JavaFileObjects.forSourceString("test.Task",
            """
            package test;
            public record Task(String title, String description) {}
            """);

        JavaFileObject source = JavaFileObjects.forSourceString("test.TaskAgent",
            """
            package test;
            import io.casehub.platform.agent.AgentService;
            import io.casehub.platform.agent.Query;
            import io.casehub.platform.agent.Param;

            @AgentService(model = "fast")
            public interface TaskAgent {
                @Query("Get task ${id}")
                Task getTask(@Param("id") String id);
            }
            """);

        Compilation compilation = Compiler.javac()
            .withProcessors(new AgentServiceProcessor())
            .compile(taskType, source);

        assertThat(compilation).succeeded();
        String proxy = readGenerated(compilation, "test.TaskAgentProxy");
        assertThat(proxy).contains("ResponseFormat.json(\"Task\"");
        assertThat(proxy).contains("config = config.withResponseFormat");
        assertThat(proxy).contains("objectMapper.readValue(result.text(), test.Task.class)");
        assertThat(proxy).contains("AgentResponseParseException");
        assertThat(proxy).contains("private final String taskSchema");
        assertThat(proxy).contains("schemaGenerator.generate(test.Task.class)");
    }

    @Test
    void generatesProxyForListReturnType() throws IOException {
        JavaFileObject taskType = JavaFileObjects.forSourceString("test.Task",
            """
            package test;
            public record Task(String title, String description) {}
            """);

        JavaFileObject source = JavaFileObjects.forSourceString("test.TaskListAgent",
            """
            package test;
            import io.casehub.platform.agent.AgentService;
            import io.casehub.platform.agent.Query;
            import java.util.List;

            @AgentService(model = "fast")
            public interface TaskListAgent {
                @Query("List all tasks")
                List<Task> listTasks();
            }
            """);

        Compilation compilation = Compiler.javac()
            .withProcessors(new AgentServiceProcessor())
            .compile(taskType, source);

        assertThat(compilation).succeeded();
        String proxy = readGenerated(compilation, "test.TaskListAgentProxy");
        assertThat(proxy).contains("ResponseFormat.json(\"TaskList\"");
        assertThat(proxy).contains("config = config.withResponseFormat");
        assertThat(proxy).contains("TypeReference<java.util.List<test.Task>>");
        assertThat(proxy).contains("AgentResponseParseException");
    }

    @Test
    void generatesProxyForSetReturnType() throws IOException {
        JavaFileObject taskType = JavaFileObjects.forSourceString("test.Task",
            """
            package test;
            public record Task(String title, String description) {}
            """);

        JavaFileObject source = JavaFileObjects.forSourceString("test.TaskSetAgent",
            """
            package test;
            import io.casehub.platform.agent.AgentService;
            import io.casehub.platform.agent.Query;
            import java.util.Set;

            @AgentService(model = "fast")
            public interface TaskSetAgent {
                @Query("Get unique tasks")
                Set<Task> uniqueTasks();
            }
            """);

        Compilation compilation = Compiler.javac()
            .withProcessors(new AgentServiceProcessor())
            .compile(taskType, source);

        assertThat(compilation).succeeded();
        String proxy = readGenerated(compilation, "test.TaskSetAgentProxy");
        assertThat(proxy).contains("new java.util.LinkedHashSet<>(");
        assertThat(proxy).contains("TypeReference<java.util.List<test.Task>>");
    }

    @Test
    void generatesProxyForCollectionReturnType() throws IOException {
        JavaFileObject taskType = JavaFileObjects.forSourceString("test.Task",
            """
            package test;
            public record Task(String title, String description) {}
            """);

        JavaFileObject source = JavaFileObjects.forSourceString("test.TaskCollectionAgent",
            """
            package test;
            import io.casehub.platform.agent.AgentService;
            import io.casehub.platform.agent.Query;
            import java.util.Collection;

            @AgentService(model = "fast")
            public interface TaskCollectionAgent {
                @Query("Get all tasks")
                Collection<Task> allTasks();
            }
            """);

        Compilation compilation = Compiler.javac()
            .withProcessors(new AgentServiceProcessor())
            .compile(taskType, source);

        assertThat(compilation).succeeded();
        String proxy = readGenerated(compilation, "test.TaskCollectionAgentProxy");
        assertThat(proxy).contains("TypeReference<java.util.List<test.Task>>");
        assertThat(proxy).doesNotContain("LinkedHashSet");
    }

    @Test
    void generatesProxyForMixedReturnTypes() throws IOException {
        JavaFileObject taskType = JavaFileObjects.forSourceString("test.Task",
            """
            package test;
            public record Task(String title, String description) {}
            """);

        JavaFileObject source = JavaFileObjects.forSourceString("test.FullAgent",
            """
            package test;
            import io.casehub.platform.agent.AgentService;
            import io.casehub.platform.agent.AgentEvent;
            import io.casehub.platform.agent.AgentSession;
            import io.casehub.platform.agent.Query;
            import io.smallrye.mutiny.Multi;
            import java.util.List;

            @AgentService(model = "fast")
            public interface FullAgent {
                @Query("Ask something")
                String ask();

                @Query("Get task")
                Task getTask();

                @Query("List tasks")
                List<Task> listTasks();

                @Query("Stream it")
                Multi<AgentEvent> stream();

                AgentSession session();
            }
            """);

        Compilation compilation = Compiler.javac()
            .withProcessors(new AgentServiceProcessor())
            .compile(taskType, source);

        assertThat(compilation).succeeded();
        String proxy = readGenerated(compilation, "test.FullAgentProxy");
        assertThat(proxy).contains("return result.text();");
        assertThat(proxy).contains("objectMapper.readValue(result.text(), test.Task.class)");
        assertThat(proxy).contains("TypeReference<java.util.List<test.Task>>");
        assertThat(proxy).contains("agentProvider.invoke(config)");
        assertThat(proxy).contains("agentProvider.openSession(init)");
        assertThat(proxy).contains("private final String taskSchema");
    }

    @Test
    void generatedProxyHasExpandedConstructor() throws IOException {
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
        assertThat(proxy).contains("PlatformSchemaGenerator schemaGenerator");
        assertThat(proxy).contains("ObjectMapper objectMapper");
        assertThat(proxy).contains("this.schemaGenerator = schemaGenerator");
        assertThat(proxy).contains("this.objectMapper = objectMapper");
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

    @Test
    void rejectsVoidReturnType() {
        JavaFileObject source = JavaFileObjects.forSourceString("test.VoidAgent",
            """
            package test;
            import io.casehub.platform.agent.AgentService;
            import io.casehub.platform.agent.Query;

            @AgentService(model = "fast")
            public interface VoidAgent {
                @Query("Do something")
                void doIt();
            }
            """);

        Compilation compilation = Compiler.javac()
            .withProcessors(new AgentServiceProcessor())
            .compile(source);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("unsupported return type");
        assertThat(compilation).hadErrorContaining("void");
    }

    @Test
    void rejectsPrimitiveReturnType() {
        JavaFileObject source = JavaFileObjects.forSourceString("test.PrimAgent",
            """
            package test;
            import io.casehub.platform.agent.AgentService;
            import io.casehub.platform.agent.Query;

            @AgentService(model = "fast")
            public interface PrimAgent {
                @Query("Count items")
                int count();
            }
            """);

        Compilation compilation = Compiler.javac()
            .withProcessors(new AgentServiceProcessor())
            .compile(source);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("unsupported return type");
        assertThat(compilation).hadErrorContaining("int");
    }

    @Test
    void rejectsBoxedPrimitiveReturnType() {
        JavaFileObject source = JavaFileObjects.forSourceString("test.BoxedAgent",
            """
            package test;
            import io.casehub.platform.agent.AgentService;
            import io.casehub.platform.agent.Query;

            @AgentService(model = "fast")
            public interface BoxedAgent {
                @Query("Count items")
                Integer count();
            }
            """);

        Compilation compilation = Compiler.javac()
            .withProcessors(new AgentServiceProcessor())
            .compile(source);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("unsupported return type");
    }

    @Test
    void rejectsArrayReturnType() {
        JavaFileObject taskType = JavaFileObjects.forSourceString("test.Task",
            """
            package test;
            public record Task(String title, String description) {}
            """);

        JavaFileObject source = JavaFileObjects.forSourceString("test.ArrayAgent",
            """
            package test;
            import io.casehub.platform.agent.AgentService;
            import io.casehub.platform.agent.Query;

            @AgentService(model = "fast")
            public interface ArrayAgent {
                @Query("Get tasks")
                Task[] getTasks();
            }
            """);

        Compilation compilation = Compiler.javac()
            .withProcessors(new AgentServiceProcessor())
            .compile(taskType, source);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("unsupported return type");
        assertThat(compilation).hadErrorContaining("List<Task>");
    }

    @Test
    void rejectsRawCollectionReturnType() {
        JavaFileObject source = JavaFileObjects.forSourceString("test.RawListAgent",
            """
            package test;
            import io.casehub.platform.agent.AgentService;
            import io.casehub.platform.agent.Query;
            import java.util.List;

            @AgentService(model = "fast")
            @SuppressWarnings("rawtypes")
            public interface RawListAgent {
                @Query("Get items")
                List getItems();
            }
            """);

        Compilation compilation = Compiler.javac()
            .withProcessors(new AgentServiceProcessor())
            .compile(source);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("unsupported return type");
        assertThat(compilation).hadErrorContaining("type argument");
    }
}
