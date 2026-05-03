alter table links
    add column if not exists processing_owner text,
    add column if not exists processing_until timestamptz;

create index if not exists idx_links_check_claim on links (last_checked_at, processing_until, id);
