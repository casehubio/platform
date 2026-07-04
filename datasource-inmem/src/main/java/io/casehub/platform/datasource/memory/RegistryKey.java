package io.casehub.platform.datasource.memory;

/**
 * Registry key (path, tenancyId) for DataSource lookup.
 */
record RegistryKey(String path, String tenancyId) {}
