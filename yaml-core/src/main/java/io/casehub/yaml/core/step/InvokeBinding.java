package io.casehub.yaml.core.step;

import java.util.List;
import java.util.Map;

public sealed interface InvokeBinding {

    record Mcp(String tool) implements InvokeBinding {}

    record Rest(String method, String url,
                Map<String, String> headers,
                Map<String, String> body) implements InvokeBinding {
        public Rest {
            if (method == null) method = "GET";
            if (headers == null) headers = Map.of();
            if (body == null) body = Map.of();
        }
    }

    record Graphql(String query) implements InvokeBinding {}

    record Python(String script) implements InvokeBinding {}

    record Agent(String descriptor,
                 String model,
                 String timeout,
                 boolean structuredOutput) implements InvokeBinding {
        public Agent {
            if (descriptor == null)
                throw new IllegalArgumentException(
                        "Agent binding requires descriptor");
        }
    }

    record Process(String command, List<String> args,
                   String output, String timeout,
                   Map<String, String> env,
                   String workingDir,
                   String onError) implements InvokeBinding {
        public Process {
            if (command == null)
                throw new IllegalArgumentException("Process binding requires command");
            if (args == null) args = List.of();
            if (output == null) output = "json";
            if (env == null) env = Map.of();
            if (onError == null) onError = "stderr";
        }
    }
}
