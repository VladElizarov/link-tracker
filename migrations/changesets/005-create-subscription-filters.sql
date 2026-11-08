--liquibase formatted sql

--changeset student:005-create-subscription-filters
CREATE TABLE subscription_filters
(
    subscription_id BIGINT NOT NULL REFERENCES subscriptions (id) ON DELETE CASCADE,
    filter_value    TEXT   NOT NULL,
    PRIMARY KEY (subscription_id, filter_value)
);
--rollback DROP TABLE subscription_filters;
