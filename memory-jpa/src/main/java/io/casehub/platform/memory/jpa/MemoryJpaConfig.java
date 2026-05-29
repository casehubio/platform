package io.casehub.platform.memory.jpa;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/** Configuration for the JPA-backed CaseMemoryStore adapter. */
@ConfigMapping(prefix = "casehub.memory.jpa")
public interface MemoryJpaConfig {

    /** Full-text search settings for memory queries. */
    Fts fts();

    interface Fts {
        /** Whether to use PostgreSQL FTS when a question is provided. Default: true. */
        @WithDefault("true")
        boolean enabled();

        /** PostgreSQL text search configuration name (e.g. english, french). Default: english. */
        @WithDefault("english")
        String language();
    }
}
