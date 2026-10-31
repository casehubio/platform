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

    record Script(String runtime, String script, String timeout,
                  String workingDir, Map<String, String> env) implements InvokeBinding {
        public static final String PYTHON = "python3";
        public static final String NODE = "node";

        public Script {
            if (runtime == null)
                throw new IllegalArgumentException("Script binding requires runtime");
            if (script == null)
                throw new IllegalArgumentException("Script binding requires script");
            if (timeout == null) timeout = "30s";
            if (env == null) env = Map.of();
        }
    }

    record Agent(String descriptor,
                 String model,
                 Map<String, Object> modelQuery,
                 String system,
                 String user,
                 String timeout,
                 boolean structuredOutput,
                 String pool,
                 List<Object> modelChain,
                 Object responseFormat) implements InvokeBinding {
        public Agent {
            if (descriptor == null && system == null)
                throw new IllegalArgumentException(
                        "Agent binding requires descriptor or system prompt");
            if (modelQuery != null) modelQuery = Map.copyOf(modelQuery);
            if (modelChain != null) modelChain = List.copyOf(modelChain);
        }

        public Agent(String descriptor, String model,
                     Map<String, Object> modelQuery,
                     String system, String user,
                     String timeout, boolean structuredOutput) {
            this(descriptor, model, modelQuery, system, user, timeout,
                    structuredOutput, null, null, null);
        }

        public Agent(String descriptor, String model, String timeout, boolean structuredOutput) {
            this(descriptor, model, null, null, null, timeout,
                    structuredOutput, null, null, null);
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

    record Aria(String action) implements InvokeBinding {
        public Aria {
            if (action == null || action.isEmpty())
                throw new IllegalArgumentException("Aria binding requires action");
        }
    }

    record SessionOpen(String session, String system, String model,
                       Map<String, Object> modelQuery, List<Object> modelChain,
                       String pool, String timeout, String checkoutTimeout)
            implements InvokeBinding {
        public SessionOpen {
            if (session == null || session.isEmpty()) {
                throw new IllegalArgumentException("SessionOpen requires session name");
            }
            if (modelQuery != null) {modelQuery = Map.copyOf(modelQuery);}
            if (modelChain != null) {modelChain = List.copyOf(modelChain);}
        }
    }

    record SessionQuery(String session, String user,
                        Object responseFormat, String output, boolean priority)
            implements InvokeBinding {
        public SessionQuery {
            if (session == null || session.isEmpty()) {
                throw new IllegalArgumentException("SessionQuery requires session name");
            }
            if (user == null || user.isEmpty()) {
                throw new IllegalArgumentException("SessionQuery requires user prompt");
            }
        }
    }

    record SessionClear(String session) implements InvokeBinding {
        public SessionClear {
            if (session == null || session.isEmpty()) {
                throw new IllegalArgumentException("SessionClear requires session name");
            }
        }
    }

    record SessionClose(String session) implements InvokeBinding {
        public SessionClose {
            if (session == null || session.isEmpty()) {
                throw new IllegalArgumentException("SessionClose requires session name");
            }
        }
    }

}
