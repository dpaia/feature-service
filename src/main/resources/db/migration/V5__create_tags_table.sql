create sequence tag_id_seq start with 100 increment by 50;

create table tags
(
    id          bigint       not null default nextval('tag_id_seq'),
    name        varchar(50)  not null unique,
    description text,
    created_by  varchar(255) not null,
    created_at  timestamp    not null default current_timestamp,
    primary key (id)
);

create table feature_tags
(
    feature_id  bigint not null,
    tag_id      bigint not null,
    primary key (feature_id, tag_id),
    constraint fk_feature_tags_feature_id foreign key (feature_id) references features (id),
    constraint fk_feature_tags_tag_id foreign key (tag_id) references tags (id)
);
