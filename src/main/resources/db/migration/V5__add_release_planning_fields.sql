alter table releases
    add column if not exists planned_start_date   timestamp,
    add column if not exists planned_release_date timestamp,
    add column if not exists actual_release_date  timestamp,
    add column if not exists owner                varchar(255),
    add column if not exists notes                text;
