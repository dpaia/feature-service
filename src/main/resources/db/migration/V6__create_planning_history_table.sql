CREATE SEQUENCE planning_history_id_seq START WITH 100 INCREMENT BY 50;

CREATE TABLE planning_history (
    id          BIGINT NOT NULL DEFAULT nextval('planning_history_id_seq'),
    entity_type VARCHAR(50) NOT NULL,
    entity_id   BIGINT NOT NULL,
    entity_code VARCHAR(100) NOT NULL,
    change_type VARCHAR(50) NOT NULL,
    field_name  VARCHAR(255),
    old_value   VARCHAR(1000),
    new_value   VARCHAR(1000),
    rationale   VARCHAR(500),
    changed_by  VARCHAR(255) NOT NULL,
    changed_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE INDEX idx_planning_history_entity_type ON planning_history (entity_type);
CREATE INDEX idx_planning_history_entity_code ON planning_history (entity_code);
CREATE INDEX idx_planning_history_change_type ON planning_history (change_type);
CREATE INDEX idx_planning_history_changed_by  ON planning_history (changed_by);
CREATE INDEX idx_planning_history_changed_at  ON planning_history (changed_at);
