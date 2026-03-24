create sequence milestone_id_seq start with 100 increment by 50;

create table milestones
(
    id          bigint       not null default nextval('milestone_id_seq'),
    code        varchar(50)  not null unique,
    name        varchar(255) not null,
    description text,
    target_date timestamp    not null,
    actual_date timestamp,
    status      varchar(50)  not null,
    product_id  bigint       not null,
    owner       varchar(255),
    notes       text,
    created_by  varchar(255) not null,
    created_at  timestamp    not null default current_timestamp,
    updated_by  varchar(255),
    updated_at  timestamp,
    primary key (id),
    constraint fk_milestones_product_id foreign key (product_id) references products (id)
);

-- Add milestone_id column to releases table with ON DELETE SET NULL constraint
alter table releases add column milestone_id bigint;
alter table releases add constraint fk_releases_milestone_id foreign key (milestone_id) references milestones (id) on delete set null;