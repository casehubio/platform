-- DataSource descriptor persistence (platform#171)

CREATE TABLE IF NOT EXISTS datasource_descriptor (
    path              VARCHAR(1024) NOT NULL,
    tenancy_id        VARCHAR(255)  NOT NULL,
    object_type_key   VARCHAR(255)  NOT NULL,
    endpoint_path     VARCHAR(1024),
    accepted_event_types TEXT,
    properties        TEXT,
    marshaller_keys   TEXT,
    registered_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (path, tenancy_id)
);
