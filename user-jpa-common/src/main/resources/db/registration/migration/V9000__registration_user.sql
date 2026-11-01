CREATE TABLE registration_user (
    id            VARCHAR(255) NOT NULL,
    email         VARCHAR(255) NOT NULL,
    display_name  VARCHAR(255),
    tenancy_id    VARCHAR(255) NOT NULL,
    status        VARCHAR(50)  NOT NULL,
    registration_method VARCHAR(50) NOT NULL,
    created_at    TIMESTAMP    NOT NULL,
    activated_at  TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE (email, tenancy_id)
);
