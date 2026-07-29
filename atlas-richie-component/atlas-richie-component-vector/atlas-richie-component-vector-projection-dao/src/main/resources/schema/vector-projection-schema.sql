-- 由业务应用的 Flyway / Liquibase / DBA 迁移流程执行；插件不会在生产环境自动建表。

CREATE TABLE rag_vector_projection
(
    id                VARCHAR(64) PRIMARY KEY,
    tenant_id         VARCHAR(128) NOT NULL,
    knowledge_base_id VARCHAR(128) NOT NULL,
    document_ref      VARCHAR(256) NOT NULL,
    active_version_id VARCHAR(64),
    created_at        TIMESTAMP    NOT NULL,
    updated_at        TIMESTAMP    NOT NULL,
    CONSTRAINT uk_rag_vector_projection_ref UNIQUE (tenant_id, knowledge_base_id, document_ref)
);

CREATE TABLE rag_vector_projection_version
(
    id                 VARCHAR(64) PRIMARY KEY,
    projection_id      VARCHAR(64)  NOT NULL,
    source_version     VARCHAR(128) NOT NULL,
    index_name         VARCHAR(256) NOT NULL,
    embedding_space_id VARCHAR(256) NOT NULL,
    state              VARCHAR(32)  NOT NULL,
    written_records    INTEGER      NOT NULL DEFAULT 0,
    failed_records     INTEGER      NOT NULL DEFAULT 0,
    failure_reason     VARCHAR(1000),
    cleanup_after      TIMESTAMP,
    activated_at       TIMESTAMP,
    created_at         TIMESTAMP    NOT NULL,
    updated_at         TIMESTAMP    NOT NULL
);
CREATE INDEX idx_rag_vector_projection_version_cleanup
    ON rag_vector_projection_version (state, cleanup_after);
CREATE INDEX idx_rag_vector_projection_version_source
    ON rag_vector_projection_version (projection_id, source_version);

CREATE TABLE rag_vector_projection_record
(
    id         VARCHAR(64) PRIMARY KEY,
    version_id VARCHAR(64)  NOT NULL,
    vector_id  VARCHAR(256) NOT NULL,
    created_at TIMESTAMP    NOT NULL,
    CONSTRAINT uk_rag_vector_projection_record UNIQUE (version_id, vector_id)
);
CREATE INDEX idx_rag_vector_projection_record_version ON rag_vector_projection_record (version_id);

CREATE TABLE rag_vector_projection_outbox
(
    id            VARCHAR(64) PRIMARY KEY,
    event_type    VARCHAR(64) NOT NULL,
    version_id    VARCHAR(64) NOT NULL,
    state         VARCHAR(32) NOT NULL,
    attempts      INTEGER     NOT NULL DEFAULT 0,
    last_error    VARCHAR(1000),
    execute_after TIMESTAMP   NOT NULL,
    processed_at  TIMESTAMP,
    created_at    TIMESTAMP   NOT NULL,
    updated_at    TIMESTAMP   NOT NULL
);
CREATE INDEX idx_rag_vector_projection_outbox_due
    ON rag_vector_projection_outbox (event_type, state, execute_after);
