alter table comments
    add column parent_id bigint,
    add column depth      int not null default 0;

alter table comments
    add constraint fk_comments_parent_id foreign key (parent_id) references comments (id);
