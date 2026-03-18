package com.sivalabs.ft.features.domain.entities;

import com.sivalabs.ft.features.domain.models.ChangeType;
import com.sivalabs.ft.features.domain.models.EntityType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "planning_history")
public class PlanningHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "planning_history_id_gen")
    @SequenceGenerator(name = "planning_history_id_gen", sequenceName = "planning_history_id_seq")
    @Column(name = "id", nullable = false)
    private Long id;

    @NotNull @Column(name = "entity_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private EntityType entityType;

    @NotNull @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Size(max = 100) @NotNull @Column(name = "entity_code", nullable = false, length = 100)
    private String entityCode;

    @NotNull @Column(name = "change_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private ChangeType changeType;

    @Size(max = 255) @Column(name = "field_name")
    private String fieldName;

    @Size(max = 1000) @Column(name = "old_value", length = 1000)
    private String oldValue;

    @Size(max = 1000) @Column(name = "new_value", length = 1000)
    private String newValue;

    @Size(max = 500) @Column(name = "rationale", length = 500)
    private String rationale;

    @Size(max = 255) @NotNull @Column(name = "changed_by", nullable = false)
    private String changedBy;

    @NotNull @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public EntityType getEntityType() {
        return entityType;
    }

    public void setEntityType(EntityType entityType) {
        this.entityType = entityType;
    }

    public Long getEntityId() {
        return entityId;
    }

    public void setEntityId(Long entityId) {
        this.entityId = entityId;
    }

    public String getEntityCode() {
        return entityCode;
    }

    public void setEntityCode(String entityCode) {
        this.entityCode = entityCode;
    }

    public ChangeType getChangeType() {
        return changeType;
    }

    public void setChangeType(ChangeType changeType) {
        this.changeType = changeType;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getOldValue() {
        return oldValue;
    }

    public void setOldValue(String oldValue) {
        this.oldValue = oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public void setNewValue(String newValue) {
        this.newValue = newValue;
    }

    public String getRationale() {
        return rationale;
    }

    public void setRationale(String rationale) {
        this.rationale = rationale;
    }

    public String getChangedBy() {
        return changedBy;
    }

    public void setChangedBy(String changedBy) {
        this.changedBy = changedBy;
    }

    public Instant getChangedAt() {
        return changedAt;
    }

    public void setChangedAt(Instant changedAt) {
        this.changedAt = changedAt;
    }
}
