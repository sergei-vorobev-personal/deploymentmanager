CREATE TABLE application
(
    id            VARCHAR PRIMARY KEY,
    function_name VARCHAR,
    s3_key        VARCHAR   NOT NULL,
    s3_bucket     VARCHAR   NOT NULL,
    state         VARCHAR   NOT NULL,
    url           VARCHAR,
    error         TEXT,
    created_at    TIMESTAMP NOT NULL,
    updated_at    TIMESTAMP
);

CREATE TABLE pending_deployment
(
    id            VARCHAR PRIMARY KEY,
    function_name VARCHAR,
    operation     VARCHAR   NOT NULL,
    is_locked     BOOLEAN DEFAULT FALSE,
    attempts      INT       NOT NULL,
    max_attempts  INT       NOT NULL,
    created_at    TIMESTAMP NOT NULL,
    updated_at    TIMESTAMP NOT NULL
);