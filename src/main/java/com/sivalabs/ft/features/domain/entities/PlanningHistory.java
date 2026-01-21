package com.sivalabs.ft.features.domain.entities;

import com.sivalabs.ft.features.domain.models.ChangeType;
import com.sivalabs.ft.features.domain.models.EntityType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "planning_history")
public class PlanningHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @NotNull @Column(name = "entity_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private EntityType entityType;

    @NotNull @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Size(max = 100) @NotNull @Column(name = "entity_code", nullable = false, length = 100)
    private String entityCode;

    @NotNull @Column(name = "change_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ChangeType changeType;

    @Size(max = 100) @Column(name = "field_name", length = 100)
    private String fieldName;

    @Size(max = 1000) @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;

    @Size(max = 1000) @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    @Size(max = 500) @Column(name = "rationale", columnDefinition = "TEXT")
    private String rationale;

    @Size(max = 100) @NotNull @Column(name = "changed_by", nullable = false, length = 100)
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

    public Long getEntityId() {
        return entityId;
    }

    public void setEntityId(Long entityId) {
        this.entityId = entityId;
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
