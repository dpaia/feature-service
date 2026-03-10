alter table features
    add column planned_completion_at  timestamp,
    add column actual_completion_at   timestamp,
    add column feature_planning_status varchar(50),
    add column feature_owner          varchar(255),
    add column blockage_reason        text;

create index idx_features_feature_planning_status on features (feature_planning_status);
create index idx_features_feature_owner on features (feature_owner);
