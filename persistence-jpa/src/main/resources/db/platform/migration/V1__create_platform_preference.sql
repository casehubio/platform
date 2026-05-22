CREATE TABLE platform_preference (
    id          BIGINT        NOT NULL PRIMARY KEY,
    scope       VARCHAR(500)  NOT NULL,
    namespace   VARCHAR(100)  NOT NULL,
    pref_name   VARCHAR(100)  NOT NULL,
    sub_key     VARCHAR(100)  NOT NULL,
    pref_value  VARCHAR(4000) NOT NULL,
    CONSTRAINT uq_platform_preference UNIQUE (scope, namespace, pref_name, sub_key)
);

CREATE INDEX idx_platform_preference_scope ON platform_preference (scope);

CREATE SEQUENCE platform_preference_seq START WITH 1 INCREMENT BY 50;
