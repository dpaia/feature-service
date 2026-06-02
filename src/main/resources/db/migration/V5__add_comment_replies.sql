alter table comments
    add column parent_comment_id bigint;

alter table comments
    add constraint fk_comments_parent_comment_id foreign key (parent_comment_id) references comments (id);
