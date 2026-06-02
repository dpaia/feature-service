create table users
(
    id       bigserial primary key,
    username varchar(255) not null unique,
    email    varchar(255) not null
);

create index idx_users_username on users(username);

create table notifications
(
    id                uuid primary key default gen_random_uuid(),
    recipient_user_id varchar(255) not null,
    recipient_email   varchar(255),
    event_type        varchar(50)  not null check (event_type in ('FEATURE_CREATED', 'FEATURE_UPDATED', 'FEATURE_DELETED', 'RELEASE_CREATED', 'RELEASE_UPDATED', 'RELEASE_DELETED')),
    event_details     text,
    link              varchar(500),
    created_at        timestamp    not null default current_timestamp,
    read              boolean      not null default false,
    read_at           timestamp,
    delivery_status   varchar(50)  not null default 'PENDING' check (delivery_status in ('PENDING', 'DELIVERED', 'FAILED'))
);

create index idx_notifications_recipient_user_id on notifications(recipient_user_id);
create index idx_notifications_created_at on notifications(created_at);
create index idx_notifications_read on notifications(read);
create index idx_notifications_delivery_status on notifications(delivery_status);
create index idx_notifications_recipient_unread on notifications(recipient_user_id, read, created_at desc);
