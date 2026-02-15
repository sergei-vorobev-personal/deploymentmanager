CREATE TABLE application
(
    id            VARCHAR PRIMARY KEY,
    state         VARCHAR   NOT NULL,
    created_at    TIMESTAMP NOT NULL,
    function_name VARCHAR,
    s3_key        VARCHAR   NOT NULL,
    s3_bucket     VARCHAR   NOT NULL,
    error         TEXT,
    updated_at    TIMESTAMP
);