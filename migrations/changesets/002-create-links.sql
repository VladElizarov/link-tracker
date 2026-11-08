--liquibase formatted sql

--changeset student:002-create-links
CREATE TABLE links
(
    id              BIGSERIAL PRIMARY KEY,
    url             TEXT NOT NULL,
    last_update     TIMESTAMPTZ,
    last_checked_at TIMESTAMPTZ,
    CONSTRAINT uq_links_url UNIQUE (url)
);

CREATE INDEX idx_links_last_checked_at ON links (last_checked_at);
--rollback DROP TABLE links;
