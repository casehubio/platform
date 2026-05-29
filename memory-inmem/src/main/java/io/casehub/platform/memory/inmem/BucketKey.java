package io.casehub.platform.memory.inmem;

import io.casehub.platform.api.memory.MemoryDomain;

record BucketKey(String tenantId, String entityId, MemoryDomain domain) {}
