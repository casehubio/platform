package io.casehub.platform.graphql.generator.infer;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import io.casehub.platform.graphql.generator.GraphQLResolverProcessor;
import org.junit.jupiter.api.Test;

import javax.tools.JavaFileObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;

class AstInferenceIntegrationTest {

    private static final String CONNECTOR_SOURCE = """
            package test.connector;

            import io.casehub.platform.api.mcp.McpDomain;
            import io.casehub.platform.api.mcp.PlatformQuery;
            import jakarta.inject.Inject;

            @McpDomain("connectors/test-bank")
            public class TestBankConnector {

                @Inject TestBankService bankService;

                @PlatformQuery("Get balance")
                public String balance(String platform, String accountId) {
                    TestSpi p = bankService.platform(platform);
                    requireCapability(p, TestCapability.class, "balance");
                    return p.testCapability(userId()).balance(accountId);
                }

                @PlatformQuery("List accounts")
                public String listAccounts(String platform) {
                    TestSpi p = bankService.platform(platform);
                    return p.listAll();
                }

                private String userId() { return "user"; }
                private static void requireCapability(Object p, Class<?> cap, String op) {}
            }
            """;

    private static final String SERVICE_SOURCE = """
            package test.connector;

            public class TestBankService {
                public TestSpi platform(String name) { return null; }
            }
            """;

    private static final String SPI_SOURCE = """
            package test.connector;

            public class TestSpi {
                public TestCapability testCapability(String userId) { return null; }
                public String listAll() { return null; }
            }
            """;

    private static final String CAPABILITY_SOURCE = """
            package test.connector;

            public class TestCapability {
                public String balance(String accountId) { return null; }
            }
            """;

    @Test
    void ast_inference_generates_plugin_with_correct_call_chain() throws IOException {
        Compilation compilation = javac()
                .withProcessors(new GraphQLResolverProcessor())
                .withOptions("-AgeneratePlugins=true", "-AgenerateGraphQL=false", "-AgenerateRest=false",
                        "-AdomainFilter=connectors/test-bank")
                .compile(
                        JavaFileObjects.forSourceString("test.connector.TestBankConnector", CONNECTOR_SOURCE),
                        JavaFileObjects.forSourceString("test.connector.TestBankService", SERVICE_SOURCE),
                        JavaFileObjects.forSourceString("test.connector.TestSpi", SPI_SOURCE),
                        JavaFileObjects.forSourceString("test.connector.TestCapability", CAPABILITY_SOURCE));

        Optional<JavaFileObject> balancePlugin = compilation.generatedSourceFiles().stream()
                .filter(f -> f.getName().contains("TestBankBalancePlugin"))
                .findFirst();

        assertThat(balancePlugin).as("Balance plugin should be generated").isPresent();

        String source = new String(balancePlugin.get().openInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(source).contains("resolved via: AST inference");
        assertThat(source).contains("testCapability(");
        assertThat(source).contains(".balance(accountId)");
    }

    @Test
    void ast_inference_generates_plugin_without_capability_for_direct_call() throws IOException {
        Compilation compilation = javac()
                .withProcessors(new GraphQLResolverProcessor())
                .withOptions("-AgeneratePlugins=true", "-AgenerateGraphQL=false", "-AgenerateRest=false",
                        "-AdomainFilter=connectors/test-bank")
                .compile(
                        JavaFileObjects.forSourceString("test.connector.TestBankConnector", CONNECTOR_SOURCE),
                        JavaFileObjects.forSourceString("test.connector.TestBankService", SERVICE_SOURCE),
                        JavaFileObjects.forSourceString("test.connector.TestSpi", SPI_SOURCE),
                        JavaFileObjects.forSourceString("test.connector.TestCapability", CAPABILITY_SOURCE));

        Optional<JavaFileObject> listPlugin = compilation.generatedSourceFiles().stream()
                .filter(f -> f.getName().contains("TestBankListAccountsPlugin"))
                .findFirst();

        assertThat(listPlugin).as("ListAccounts plugin should be generated").isPresent();

        String source = new String(listPlugin.get().openInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(source).contains("resolved via: AST inference");
        assertThat(source).contains(".listAll()");
        assertThat(source).doesNotContain("testCapability(");
    }


    private static final String HAND_WRITTEN_PLUGIN = """
                                                      package test.connector;
                                                      
                                                      import io.casehub.yaml.plugin.api.Plugin;
                                                      import io.casehub.yaml.plugin.api.Execute;
                                                      import io.casehub.yaml.plugin.api.Required;
                                                      import io.casehub.yaml.plugin.api.Result;
                                                      
                                                      @Plugin(value = "test-bank-balance", description = "Hand-written balance plugin")
                                                      public record HandWrittenBalancePlugin(@Required String accountId) {
                                                          @Execute
                                                          public Result execute(TestCapability api) {
                                                              return Result.of(java.util.Map.of("result", api.balance(accountId)));
                                                          }
                                                      }
                                                      """;


    private static final String SUBJECT_INTERFACE_SOURCE = """
                                                           package test.connector;
                                                           
                                                           import io.casehub.platform.api.mcp.McpDomain;
                                                           import io.casehub.platform.api.mcp.PlatformQuery;
                                                           import io.casehub.platform.api.mcp.GeneratePlugin;
                                                           
                                                           @McpDomain("test-voice")
                                                           public interface TestVoiceApi {
                                                           
                                                               @PlatformQuery("Speak text")
                                                               @GeneratePlugin(subject = "text", absorb = true)
                                                               String speak(String text, String voice);
                                                           }
                                                           """;


    @Test
    void subject_field_is_first_and_required() throws IOException {
        Compilation compilation = javac()
                                          .withProcessors(new GraphQLResolverProcessor())
                                          .withOptions("-AgeneratePlugins=true", "-AgenerateGraphQL=false", "-AgenerateRest=false",
                                                       "-AdomainFilter=test-voice")
                                          .compile(
                                                  JavaFileObjects.forSourceString("test.connector.TestVoiceApi", SUBJECT_INTERFACE_SOURCE));

        Optional<JavaFileObject> plugin = compilation.generatedSourceFiles().stream()
                                                     .filter(f -> f.getName().contains("VoiceSpeakPlugin"))
                                                     .findFirst();

        assertThat(plugin).as("Speak plugin should be generated").isPresent();

        String source = new String(plugin.get().openInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(source).contains("absorbSiblings = true");
        assertThat(source).contains("@Required String text");
        int textPos  = source.indexOf("@Required String text");
        int voicePos = source.indexOf("String voice");
        assertThat(textPos).as("text (subject) should appear before voice in record").isLessThan(voicePos);
    }


    @Test
    void hand_written_plugin_with_same_name_skips_generated() throws IOException {
        Compilation compilation = javac()
                                          .withProcessors(new GraphQLResolverProcessor())
                                          .withOptions("-AgeneratePlugins=true", "-AgenerateGraphQL=false", "-AgenerateRest=false",
                                                       "-AdomainFilter=connectors/test-bank")
                                          .compile(
                                                  JavaFileObjects.forSourceString("test.connector.TestBankConnector", CONNECTOR_SOURCE),
                                                  JavaFileObjects.forSourceString("test.connector.TestBankService", SERVICE_SOURCE),
                                                  JavaFileObjects.forSourceString("test.connector.TestSpi", SPI_SOURCE),
                                                  JavaFileObjects.forSourceString("test.connector.TestCapability", CAPABILITY_SOURCE),
                                                  JavaFileObjects.forSourceString("test.connector.HandWrittenBalancePlugin", HAND_WRITTEN_PLUGIN));

        Optional<JavaFileObject> balancePlugin = compilation.generatedSourceFiles().stream()
                                                            .filter(f -> f.getName().contains("TestBankBalancePlugin"))
                                                            .findFirst();

        assertThat(balancePlugin).as("Generated balance plugin should be skipped — hand-written exists").isEmpty();

        Optional<JavaFileObject> listPlugin = compilation.generatedSourceFiles().stream()
                                                         .filter(f -> f.getName().contains("TestBankListAccountsPlugin"))
                                                         .findFirst();

        assertThat(listPlugin).as("ListAccounts plugin should still be generated").isPresent();
    }

}
