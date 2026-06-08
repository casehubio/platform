package io.casehub.platform.memory.graphiti.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AddMessagesRequest(
        @JsonProperty("group_id")  String groupId,
        @JsonProperty("messages")  List<AddMessage> messages
) {}
