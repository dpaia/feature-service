-- Create sequence for planning_history table
create sequence planning_history_id_seq start with 100 increment by 50;

-- Create planning_history table
create table planning_history
(
    id          bigint       not null default nextval('planning_history_id_seq'),
    entity_type varchar(50)  not null,
    entity_id   bigint       not null,
    entity_code varchar(50)  not null,
    change_type varchar(50)  not null,
    field_name  varchar(255),
    old_value   varchar(1000),
    new_value   varchar(1000),
    rationale   varchar(500),
    changed_by  varchar(255) not null,
    changed_at  timestamp    not null default current_timestamp,
    primary key (id)
);

-- Create indexes for better query performance
CREATE INDEX idx_planning_history_entity ON planning_history(entity_type, entity_id);
CREATE INDEX idx_planning_history_entity_code ON planning_history(entity_code);
CREATE INDEX idx_planning_history_changed_at ON planning_history(changed_at);
CREATE INDEX idx_planning_history_changed_by ON planning_history(changed_by);