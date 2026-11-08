--liquibase formatted sql

--changeset student:001-create-chats
CREATE TABLE chats
(
    id         BIGINT PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
--rollback DROP TABLE chats;
