truncate table subscription_filters, subscription_tags, subscriptions, links, chats restart identity cascade;

insert into chats (id, registered_at)
select 900000000000 + chat_number, now()
from generate_series(1, 1000) as chat_number;

insert into links (id, url, created_at, last_checked_at, last_updated_at, last_event_at, last_event_cursor)
select
    link_number,
    'https://github.com/load-test/repo-' || link_number,
    now(),
    now(),
    now(),
    now(),
    null
from generate_series(1, 100000) as link_number;

insert into subscriptions (chat_id, link_id, created_at)
select
    900000000000 + chat_number,
    ((chat_number - 1) * 100) + link_offset,
    now()
from generate_series(1, 1000) as chat_number
cross join generate_series(1, 100) as link_offset;

insert into subscription_tags (chat_id, link_id, tag)
select chat_id, link_id, 'load-test'
from subscriptions;

insert into subscription_filters (chat_id, link_id, filter_value)
select chat_id, link_id, 'author:load-test'
from subscriptions;

select setval('links_id_seq', (select max(id) from links));
