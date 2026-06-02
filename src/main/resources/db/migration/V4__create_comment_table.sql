create sequence comment_id_seq start with 100 increment by 50;

create table comments
(
    id                bigint       not null default nextval('comment_id_seq'),
    feature_id        bigint references features (id),
    release_id        bigint references releases (id),
    parent_comment_id bigint references comments (id),
    created_by        varchar(255) not null,
    content           text         not null,
    created_at        timestamp    not null default current_timestamp,
    primary key (id)
);
