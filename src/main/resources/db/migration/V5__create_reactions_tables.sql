-- Create sequence for feature reactions
create sequence feature_reaction_id_seq start with 100 increment by 50;

-- Create feature reactions table
create table feature_reactions(
    id bigint not null primary key default nextval('feature_reaction_id_seq'),
    feature_id bigint references features(id) not null,
    user_id varchar(255) not null,
    reaction_type varchar(20) not null,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp,
    UNIQUE (feature_id, user_id)
);

-- Create indexes for better performance
create index idx_feature_reactions_feature_id on feature_reactions(feature_id);