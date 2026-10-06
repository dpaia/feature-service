create table feature_products
(
    feature_id bigint not null,
    product_id bigint not null,
    primary key (feature_id, product_id),
    constraint fk_feature_products_feature_id foreign key (feature_id) references features (id),
    constraint fk_feature_products_product_id foreign key (product_id) references products (id)
);

insert into feature_products (feature_id, product_id)
select id, product_id
from features
where product_id is not null;

alter table features
    drop constraint fk_features_product_id;

alter table features
    drop column product_id;
