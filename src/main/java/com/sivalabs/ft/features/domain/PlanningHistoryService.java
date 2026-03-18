package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.domain.dtos.PlanningHistoryDto;
import com.sivalabs.ft.features.domain.entities.Feature;
import com.sivalabs.ft.features.domain.entities.PlanningHistory;
import com.sivalabs.ft.features.domain.entities.Release;
import com.sivalabs.ft.features.domain.mappers.PlanningHistoryMapper;
import com.sivalabs.ft.features.domain.models.ChangeType;
import com.sivalabs.ft.features.domain.models.EntityType;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlanningHistoryService {
    private static final int MAX_VALUE_LENGTH = 1000;
    private static final int MAX_RATIONALE_LENGTH = 500;

    private final PlanningHistoryRepository planningHistoryRepository;
    private final PlanningHistoryMapper planningHistoryMapper;

    PlanningHistoryService(
            PlanningHistoryRepository planningHistoryRepository, PlanningHistoryMapper planningHistoryMapper) {
        this.planningHistoryRepository = planningHistoryRepository;
        this.planningHistoryMapper = planningHistoryMapper;
    }

    @Transactional(readOnly = true)
    public Page<PlanningHistoryDto> findHistory(
            EntityType entityType,
            String entityCode,
            String changedBy,
            ChangeType changeType,
            Instant dateFrom,
            Instant dateTo,
            int page,
            int size,
            String sort) {
        Sort sortOrder = parseSort(sort);
        Pageable pageable = PageRequest.of(page, size, sortOrder);
        Specification<PlanningHistory> spec =
                buildSpec(entityType, entityCode, changedBy, changeType, dateFrom, dateTo);
        return planningHistoryRepository.findAll(spec, pageable).map(planningHistoryMapper::toDto);
    }

    @Transactional
    public void recordFeatureCreated(Feature feature) {
        save(
                EntityType.FEATURE,
                feature.getId(),
                feature.getCode(),
                ChangeType.CREATED,
                null,
                null,
                null,
                feature.getCreatedBy());
    }

    @Transactional
    public void recordFeatureDeleted(Feature feature, String deletedBy) {
        save(EntityType.FEATURE, feature.getId(), feature.getCode(), ChangeType.DELETED, null, null, null, deletedBy);
    }

    @Transactional
    public void recordFeatureFieldChange(
            Feature feature,
            String fieldName,
            String oldValue,
            String newValue,
            ChangeType changeType,
            String changedBy) {
        save(
                EntityType.FEATURE,
                feature.getId(),
                feature.getCode(),
                changeType,
                fieldName,
                truncate(oldValue, MAX_VALUE_LENGTH),
                truncate(newValue, MAX_VALUE_LENGTH),
                changedBy);
    }

    @Transactional
    public void recordReleaseCreated(Release release) {
        save(
                EntityType.RELEASE,
                release.getId(),
                release.getCode(),
                ChangeType.CREATED,
                null,
                null,
                null,
                release.getCreatedBy());
    }

    @Transactional
    public void recordReleaseDeleted(Release release, String deletedBy) {
        save(EntityType.RELEASE, release.getId(), release.getCode(), ChangeType.DELETED, null, null, null, deletedBy);
    }

    @Transactional
    public void recordReleaseFieldChange(
            Release release,
            String fieldName,
            String oldValue,
            String newValue,
            ChangeType changeType,
            String changedBy) {
        save(
                EntityType.RELEASE,
                release.getId(),
                release.getCode(),
                changeType,
                fieldName,
                truncate(oldValue, MAX_VALUE_LENGTH),
                truncate(newValue, MAX_VALUE_LENGTH),
                changedBy);
    }

    private void save(
            EntityType entityType,
            Long entityId,
            String entityCode,
            ChangeType changeType,
            String fieldName,
            String oldValue,
            String newValue,
            String changedBy) {
        PlanningHistory history = new PlanningHistory();
        history.setEntityType(entityType);
        history.setEntityId(entityId);
        history.setEntityCode(entityCode);
        history.setChangeType(changeType);
        history.setFieldName(fieldName);
        history.setOldValue(oldValue);
        history.setNewValue(newValue);
        history.setChangedBy(changedBy);
        history.setChangedAt(Instant.now());
        planningHistoryRepository.save(history);
    }

    private Specification<PlanningHistory> buildSpec(
            EntityType entityType,
            String entityCode,
            String changedBy,
            ChangeType changeType,
            Instant dateFrom,
            Instant dateTo) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (entityType != null) {
                predicates.add(cb.equal(root.get("entityType"), entityType));
            }
            if (entityCode != null) {
                predicates.add(cb.equal(root.get("entityCode"), entityCode));
            }
            if (changedBy != null) {
                predicates.add(cb.equal(root.get("changedBy"), changedBy));
            }
            if (changeType != null) {
                predicates.add(cb.equal(root.get("changeType"), changeType));
            }
            if (dateFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("changedAt"), dateFrom));
            }
            if (dateTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("changedAt"), dateTo));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Sort parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Direction.DESC, "changedAt");
        }
        String[] parts = sort.split(",");
        String field = parts[0].trim();
        Sort.Direction direction =
                parts.length > 1 && "asc".equalsIgnoreCase(parts[1].trim()) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, field);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}
