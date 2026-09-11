package io.casehub.platform.memory.inmem;

import io.casehub.neocortex.memory.MemoryDomain;

record BucketKey(String tenantId, String entityId, MemoryDomain domain) {}
