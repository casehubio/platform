package io.casehub.client.dto;

import io.casehub.platform.graphql.PageInfo;

import java.util.List;

public record WorkItemPage(List<WorkItem> items, PageInfo pageInfo) {
}
