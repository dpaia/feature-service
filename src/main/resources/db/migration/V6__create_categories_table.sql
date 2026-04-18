create sequence category_id_seq start with 100 increment by 50;

create table categories
(
    id                 bigint       not null default nextval('category_id_seq'),
    name               varchar(50)  not null unique,
    description        text,
    parent_category_id bigint,
    created_by         varchar(255) not null,
    created_at         timestamp    not null default current_timestamp,
    primary key (id)
);

alter table features add column category_id bigint,
    add constraint fk_features_category
    foreign key (category_id) references categories (id);
