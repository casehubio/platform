CREATE TABLE registration_password_credential (
    actor_id      VARCHAR(255) NOT NULL,
    tenancy_id    VARCHAR(255) NOT NULL,
    hashed_password VARCHAR(255) NOT NULL,
    created_at    TIMESTAMP    NOT NULL,
    last_used_at  TIMESTAMP,
    PRIMARY KEY (actor_id, tenancy_id)
);
