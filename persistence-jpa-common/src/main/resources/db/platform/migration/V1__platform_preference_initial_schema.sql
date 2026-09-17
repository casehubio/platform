-- Consolidated initial schema for platform preferences.
-- Replaces V1–V2 incremental migrations (no production database exists).

CREATE SEQUENCE platform_preference_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE platform_preference (
    id          BIGINT        NOT NULL PRIMARY KEY,
    tenancy_id  VARCHAR(100)  NOT NULL,
    scope       VARCHAR(500)  NOT NULL,
    namespace   VARCHAR(100)  NOT NULL,
    pref_name   VARCHAR(100)  NOT NULL,
    sub_key     VARCHAR(100)  NOT NULL,
    pref_value  VARCHAR(4000) NOT NULL,
    CONSTRAINT uq_platform_preference UNIQUE (tenancy_id, scope, namespace, pref_name, sub_key)
);

CREATE INDEX idx_platform_preference_scope ON platform_preference (scope);
