--liquibase formatted sql

--changeset student:004-create-tags
CREATE TABLE tags
(
    id      BIGSERIAL PRIMARY KEY,
    chat_id BIGINT NOT NULL REFERENCES chats (id) ON DELETE CASCADE,
    name    TEXT   NOT NULL,
    CONSTRAINT uq_tags_chat_name UNIQUE (chat_id, name)
);

CREATE TABLE subscription_tags
(
    subscription_id BIGINT NOT NULL REFERENCES subscriptions (id) ON DELETE CASCADE,
    tag_id          BIGINT NOT NULL REFERENCES tags (id) ON DELETE CASCADE,
    PRIMARY KEY (subscription_id, tag_id)
);

CREATE INDEX idx_subscription_tags_tag_id ON subscription_tags (tag_id);
--rollback DROP TABLE subscription_tags; DROP TABLE tags;
