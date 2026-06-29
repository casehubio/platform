package io.casehub.platform.agent.claude;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.platform.agent.AgentEvent;
import org.jboss.logging.Logger;
import org.springaicommunity.claude.agent.sdk.types.AssistantMessage;
import org.springaicommunity.claude.agent.sdk.types.ContentBlock;
import org.springaicommunity.claude.agent.sdk.types.Message;
import org.springaicommunity.claude.agent.sdk.types.TextBlock;
import org.springaicommunity.claude.agent.sdk.types.ThinkingBlock;
import org.springaicommunity.claude.agent.sdk.types.ToolUseBlock;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Maps Claude SDK {@link Message} objects to {@link AgentEvent} instances.
 *
 * <p>Only {@link AssistantMessage} content blocks are mapped — all other message
 * types (ResultMessage, UserMessage, SystemMessage) are silently skipped.
 *
 * <p>The Claude SDK delivers complete content blocks, not streaming fragments.
 * {@link AgentEvent.ToolCallDelta} is never emitted by this mapper.
 */
class MessageEventMapper {

    private static final Logger LOG = Logger.getLogger(MessageEventMapper.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static List<AgentEvent> toEvents(final Message message, final AtomicInteger toolIndex) {
        if (!(message instanceof AssistantMessage am)) return List.of();
        final List<AgentEvent> events = new ArrayList<>();
        for (final ContentBlock block : am.content()) {
            switch (block) {
                case TextBlock tb -> {
                    if (tb.text() != null && !tb.text().isEmpty())
                        events.add(new AgentEvent.TextDelta(tb.text()));
                }
                case ThinkingBlock tb -> {
                    if (tb.thinking() != null && !tb.thinking().isEmpty())
                        events.add(new AgentEvent.ThinkingDelta(tb.thinking()));
                }
                case ToolUseBlock tu -> {
                    final String args = serializeInput(tu.input());
                    events.add(new AgentEvent.ToolCallComplete(
                        toolIndex.getAndIncrement(), tu.id(), tu.name(), args));
                }
                default -> LOG.debugf("Skipping unrecognized ContentBlock type: %s",
                    block.getType());
            }
        }
        return events;
    }

    private static String serializeInput(final Map<String, Object> input) {
        if (input == null || input.isEmpty()) return "{}";
        try {
            return MAPPER.writeValueAsString(input);
        } catch (final Exception e) {
            LOG.warnf("Failed to serialize tool input, defaulting to {}: %s", e.getMessage());
            return "{}";
        }
    }
}
