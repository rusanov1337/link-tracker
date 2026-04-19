alter table links
    add column if not exists processing_owner text,
    add column if not exists processing_until timestamptz;

create index if not exists idx_links_processing_until on links (processing_until);
