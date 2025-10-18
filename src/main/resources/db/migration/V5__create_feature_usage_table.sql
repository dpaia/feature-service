create sequence feature_usage_id_seq start with 1 increment by 50;

create table feature_usage
(
    id           bigint       not null default nextval('feature_usage_id_seq'),
    user_id      varchar(255) not null,
    feature_code varchar(50),
    product_code varchar(50),
    release_code varchar(50),
    action_type  varchar(50)  not null,
    timestamp    timestamp    not null default current_timestamp,
    context      text,
    ip_address   varchar(45),
    user_agent   varchar(500),
    primary key (id)
);

create index idx_feature_usage_user_id on feature_usage (user_id);
create index idx_feature_usage_feature_code on feature_usage (feature_code);
create index idx_feature_usage_product_code on feature_usage (product_code);
create index idx_feature_usage_release_code on feature_usage (release_code);
create index idx_feature_usage_action_type on feature_usage (action_type);
create index idx_feature_usage_timestamp on feature_usage (timestamp);
create index idx_feature_usage_timestamp_action on feature_usage (timestamp, action_type);
