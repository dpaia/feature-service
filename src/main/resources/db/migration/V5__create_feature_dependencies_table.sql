create sequence feature_dependency_id_seq start with 100 increment by 50;

create table feature_dependencies
(
    id                       bigint       not null default nextval('feature_dependency_id_seq'),
    feature_code             varchar(50)  not null,
    depends_on_feature_code  varchar(50)  not null,
    dependency_type          varchar(20)  not null check (dependency_type in ('HARD', 'SOFT', 'OPTIONAL')),
    notes                    varchar(1000),
    created_at               timestamp    not null default current_timestamp,
    primary key (id),
    constraint fk_feature_dependencies_feature_code foreign key (feature_code) references features (code),
    constraint fk_feature_dependencies_depends_on_feature_code foreign key (depends_on_feature_code) references features (code),
    constraint unique_feature_dependency unique (feature_code, depends_on_feature_code),
    constraint check_no_self_dependency check (feature_code != depends_on_feature_code)
);