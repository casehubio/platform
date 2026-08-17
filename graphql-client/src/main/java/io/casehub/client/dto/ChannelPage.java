package io.casehub.client.dto;

import io.casehub.platform.graphql.PageInfo;

import java.util.List;

public record ChannelPage(List<Channel> items, PageInfo pageInfo) {
}
