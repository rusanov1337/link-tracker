create table if not exists notification_outbox (
    id bigserial primary key,
    event_type text not null,
    payload text not null,
    created_at timestamptz not null,
    processed_at timestamptz,
    processing_owner text,
    processing_until timestamptz
);

create index if not exists idx_notification_outbox_event_type_unprocessed
    on notification_outbox (event_type, processed_at, id);

create index if not exists idx_notification_outbox_processing_until
    on notification_outbox (processing_until);
