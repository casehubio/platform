package io.casehub.platform.agent.langchain4j;

import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import io.casehub.platform.agent.AgentEvent;

/** Extends LangChain4j metadata with CaseHub routing observability. */
public class CaseHubChatResponseMetadata extends ChatResponseMetadata {

    private final String backendKey;
    private final String instanceId;
    private final Double costUsd;

    private CaseHubChatResponseMetadata(Builder builder) {
        super(builder);
        this.backendKey = builder.backendKey;
        this.instanceId = builder.instanceId;
        this.costUsd = builder.costUsd;
    }

    public String backendKey() { return backendKey; }
    public String instanceId() { return instanceId; }
    public Double costUsd() { return costUsd; }

    static CaseHubChatResponseMetadata from(AgentEvent.InvocationComplete ic) {
        var builder = new Builder();
        builder.tokenUsage(new TokenUsage(ic.inputTokens(), ic.outputTokens()));
        builder.finishReason(ic.isError() ? FinishReason.OTHER : FinishReason.STOP);
        if (ic.backendKey() != null) {
            builder.backendKey(ic.backendKey());
            builder.modelName(ic.backendKey());
        }
        if (ic.instanceId() != null) builder.instanceId(ic.instanceId());
        if (ic.totalCostUsd() != null) builder.costUsd(ic.totalCostUsd());
        if (ic.sessionId() != null) builder.id(ic.sessionId());
        return builder.build();
    }

    public static class Builder extends ChatResponseMetadata.Builder<Builder> {
        private String backendKey;
        private String instanceId;
        private Double costUsd;

        public Builder backendKey(String backendKey) { this.backendKey = backendKey; return this; }
        public Builder instanceId(String instanceId) { this.instanceId = instanceId; return this; }
        public Builder costUsd(Double costUsd) { this.costUsd = costUsd; return this; }

        @Override
        public CaseHubChatResponseMetadata build() {
            return new CaseHubChatResponseMetadata(this);
        }
    }
}
