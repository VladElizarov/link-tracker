--liquibase formatted sql

--changeset student:003-create-subscriptions
CREATE TABLE subscriptions
(
    id         BIGSERIAL PRIMARY KEY,
    chat_id    BIGINT      NOT NULL REFERENCES chats (id) ON DELETE CASCADE,
    link_id    BIGINT      NOT NULL REFERENCES links (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_subscriptions_chat_link UNIQUE (chat_id, link_id)
);

CREATE INDEX idx_subscriptions_link_id ON subscriptions (link_id);
--rollback DROP TABLE subscriptions;
