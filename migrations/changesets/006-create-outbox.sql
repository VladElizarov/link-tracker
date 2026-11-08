--liquibase formatted sql

--changeset student:006-create-outbox
CREATE TABLE outbox
(
    id         BIGSERIAL PRIMARY KEY,
    payload    TEXT        NOT NULL,
    status     TEXT        NOT NULL DEFAULT 'NEW',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at    TIMESTAMPTZ,
    CONSTRAINT chk_outbox_status CHECK (status IN ('NEW', 'SENT'))
);

CREATE INDEX idx_outbox_new ON outbox (id) WHERE status = 'NEW';
--rollback DROP TABLE outbox;
