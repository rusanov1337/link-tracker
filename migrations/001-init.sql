create table if not exists chats (
    id bigint primary key,
    registered_at timestamptz not null
);

create table if not exists links (
    id bigserial primary key,
    url text not null,
    created_at timestamptz not null,
    last_checked_at timestamptz not null,
    last_updated_at timestamptz not null,
    constraint uq_links_url unique (url)
);

create table if not exists subscriptions (
    chat_id bigint not null,
    link_id bigint not null,
    created_at timestamptz not null,
    constraint pk_subscriptions primary key (chat_id, link_id),
    constraint fk_subscriptions_chat
        foreign key (chat_id) references chats (id) on delete cascade,
    constraint fk_subscriptions_link
        foreign key (link_id) references links (id) on delete cascade
);

create table if not exists subscription_tags (
    chat_id bigint not null,
    link_id bigint not null,
    tag text not null,
    constraint pk_subscription_tags primary key (chat_id, link_id, tag),
    constraint fk_subscription_tags_subscription
        foreign key (chat_id, link_id) references subscriptions (chat_id, link_id) on delete cascade
);

create table if not exists subscription_filters (
    chat_id bigint not null,
    link_id bigint not null,
    filter_value text not null,
    constraint pk_subscription_filters primary key (chat_id, link_id, filter_value),
    constraint fk_subscription_filters_subscription
        foreign key (chat_id, link_id) references subscriptions (chat_id, link_id) on delete cascade
);

create index if not exists idx_subscriptions_link_id on subscriptions (link_id);
create index if not exists idx_links_last_checked_at on links (last_checked_at);
create index if not exists idx_links_last_updated_at on links (last_updated_at);
create index if not exists idx_subscription_tags_tag on subscription_tags (tag);
