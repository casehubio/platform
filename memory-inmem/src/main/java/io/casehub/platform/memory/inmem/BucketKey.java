package io.casehub.platform.memory.inmem;

import io.casehub.memory.MemoryDomain;

record BucketKey(String tenantId, String entityId, MemoryDomain domain) {}
