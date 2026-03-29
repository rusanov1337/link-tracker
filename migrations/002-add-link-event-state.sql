alter table links
    add column if not exists last_event_at timestamptz,
    add column if not exists last_event_cursor text;
