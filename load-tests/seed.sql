TRUNCATE TABLE chats, links, tags, outbox RESTART IDENTITY CASCADE;

INSERT INTO chats (id)
SELECT chat_id FROM generate_series(1, 1000) AS chat_id;

INSERT INTO links (url)
SELECT 'https://github.com/load-user-' || chat_id || '/repo-' || n
FROM generate_series(1, 1000) AS chat_id, generate_series(1, 100) AS n;

INSERT INTO subscriptions (chat_id, link_id)
SELECT split_part(split_part(url, 'load-user-', 2), '/', 1)::BIGINT, id
FROM links;

ANALYZE;

SELECT (SELECT count(*) FROM chats) AS chats,
       (SELECT count(*) FROM links) AS links,
       (SELECT count(*) FROM subscriptions) AS subscriptions;
