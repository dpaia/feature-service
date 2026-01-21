package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.domain.dtos.PlanningHistoryDto;
import com.sivalabs.ft.features.domain.entities.PlanningHistory;
import com.sivalabs.ft.features.domain.exceptions.ResourceNotFoundException;
import com.sivalabs.ft.features.domain.mappers.PlanningHistoryMapper;
import com.sivalabs.ft.features.domain.models.ChangeType;
import com.sivalabs.ft.features.domain.models.EntityType;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlanningHistoryService {

    private final PlanningHistoryRepository planningHistoryRepository;
    private final PlanningHistoryMapper planningHistoryMapper;

    public PlanningHistoryService(
            PlanningHistoryRepository planningHistoryRepository, PlanningHistoryMapper planningHistoryMapper) {
        this.planningHistoryRepository = planningHistoryRepository;
        this.planningHistoryMapper = planningHistoryMapper;
    }

    /**
     * Query planning history with filters and pagination
     */
    @Transactional(readOnly = true)
    public Page<PlanningHistoryDto> queryPlanningHistory(
            EntityType entityType,
            String entityCode,
            String changedBy,
            ChangeType changeType,
            Instant dateFrom,
            Instant dateTo,
            int page,
            int size,
            String sort) {

        // Get all history records and filter in service layer
        List<PlanningHistory> allHistory = planningHistoryRepository.findAllByOrderByChangedAtDesc();

        // Apply filters
        List<PlanningHistory> filteredHistory = allHistory.stream()
                .filter(history -> entityType == null || history.getEntityType() == entityType)
                .filter(history -> entityCode == null || entityCode.equals(history.getEntityCode()))
                .filter(history -> changedBy == null || changedBy.equals(history.getChangedBy()))
                .filter(history -> changeType == null || history.getChangeType() == changeType)
                .filter(history -> dateFrom == null || !history.getChangedAt().isBefore(dateFrom))
                .filter(history -> dateTo == null || !history.getChangedAt().isAfter(dateTo))
                .collect(Collectors.toList());

        // Apply sorting
        filteredHistory = applySorting(filteredHistory, sort);

        // Apply pagination
        return applyPagination(filteredHistory, page, size);
    }

    /**
     * Get release history by release code
     */
    @Transactional(readOnly = true)
    public Page<PlanningHistoryDto> getReleaseHistory(
            String releaseCode,
            ChangeType changeType,
            Instant dateFrom,
            Instant dateTo,
            int page,
            int size,
            String sort) {

        // Get all history records for the release entity code
        List<PlanningHistory> allHistory = planningHistoryRepository.findAllByOrderByChangedAtDesc();

        // Filter for release entity type and entity code, plus other filters
        List<PlanningHistory> filteredHistory = allHistory.stream()
                .filter(history -> history.getEntityType() == EntityType.RELEASE)
                .filter(history -> releaseCode.equals(history.getEntityCode()))
                .filter(history -> changeType == null || history.getChangeType() == changeType)
                .filter(history -> dateFrom == null || !history.getChangedAt().isBefore(dateFrom))
                .filter(history -> dateTo == null || !history.getChangedAt().isAfter(dateTo))
                .collect(Collectors.toList());

        // Check if any history exists, if not, throw ResourceNotFoundException
        if (filteredHistory.isEmpty()) {
            throw new ResourceNotFoundException("Release not found: " + releaseCode);
        }

        // Apply sorting
        filteredHistory = applySorting(filteredHistory, sort);

        // Apply pagination
        return applyPagination(filteredHistory, page, size);
    }

    /**
     * Get feature history by feature code
     */
    @Transactional(readOnly = true)
    public Page<PlanningHistoryDto> getFeatureHistory(
            String featureCode,
            ChangeType changeType,
            Instant dateFrom,
            Instant dateTo,
            int page,
            int size,
            String sort) {

        // Get all history records for the feature entity code
        List<PlanningHistory> allHistory = planningHistoryRepository.findAllByOrderByChangedAtDesc();

        // Filter for feature entity type and entity code, plus other filters
        List<PlanningHistory> filteredHistory = allHistory.stream()
                .filter(history -> history.getEntityType() == EntityType.FEATURE)
                .filter(history -> featureCode.equals(history.getEntityCode()))
                .filter(history -> changeType == null || history.getChangeType() == changeType)
                .filter(history -> dateFrom == null || !history.getChangedAt().isBefore(dateFrom))
                .filter(history -> dateTo == null || !history.getChangedAt().isAfter(dateTo))
                .collect(Collectors.toList());

        // Check if any history exists, if not, throw ResourceNotFoundException
        if (filteredHistory.isEmpty()) {
            throw new ResourceNotFoundException("Feature not found: " + featureCode);
        }

        // Apply sorting
        filteredHistory = applySorting(filteredHistory, sort);

        // Apply pagination
        return applyPagination(filteredHistory, page, size);
    }

    /**
     * Record a planning change
     */
    @Transactional
    public void recordChange(
            EntityType entityType,
            String entityCode,
            Long entityId,
            ChangeType changeType,
            String fieldName,
            String oldValue,
            String newValue,
            String rationale,
            String changedBy) {

        // Skip recording if entityId is null (required by database constraint)
        if (entityId == null) {
            return;
        }

        PlanningHistory history = new PlanningHistory();
        history.setEntityType(entityType);
        history.setEntityCode(entityCode);
        history.setEntityId(entityId);
        history.setChangeType(changeType);
        history.setFieldName(fieldName);
        history.setOldValue(truncateValue(oldValue));
        history.setNewValue(truncateValue(newValue));
        history.setRationale(rationale != null ? truncateValue(rationale) : null);
        history.setChangedBy(changedBy);
        history.setChangedAt(Instant.now());

        planningHistoryRepository.save(history);
    }

    /**
     * Record a planning change (overloaded for backward compatibility)
     */
    @Transactional
    public void recordChange(
            EntityType entityType,
            String entityCode,
            ChangeType changeType,
            String fieldName,
            String oldValue,
            String newValue,
            String changedBy) {
        recordChange(entityType, entityCode, null, changeType, fieldName, oldValue, newValue, null, changedBy);
    }

    /**
     * Apply sorting to filtered history list
     */
    private List<PlanningHistory> applySorting(List<PlanningHistory> history, String sort) {
        if (sort == null || sort.isBlank()) {
            // Default sorting: changedAt desc
            return history.stream()
                    .sorted(Comparator.comparing(PlanningHistory::getChangedAt).reversed())
                    .collect(Collectors.toList());
        }

        String[] sortParts = sort.split(",");
        String property = sortParts[0];
        boolean ascending = sortParts.length > 1 && "asc".equalsIgnoreCase(sortParts[1]);

        Comparator<PlanningHistory> comparator;

        if ("changedAt".equals(property)) {
            comparator = Comparator.comparing(PlanningHistory::getChangedAt);
        } else if ("entityType".equals(property)) {
            comparator = Comparator.comparing(h -> h.getEntityType().name());
        } else if ("entityCode".equals(property)) {
            comparator = Comparator.comparing(PlanningHistory::getEntityCode);
        } else if ("changeType".equals(property)) {
            comparator = Comparator.comparing(h -> h.getChangeType().name());
        } else if ("changedBy".equals(property)) {
            comparator = Comparator.comparing(PlanningHistory::getChangedBy);
        } else {
            comparator = Comparator.comparing(PlanningHistory::getChangedAt);
        }

        if (!ascending) {
            comparator = comparator.reversed();
        }

        return history.stream().sorted(comparator).collect(Collectors.toList());
    }

    /**
     * Apply pagination to filtered and sorted history list
     */
    private Page<PlanningHistoryDto> applyPagination(List<PlanningHistory> history, int page, int size) {
        int totalElements = history.size();
        int startIndex = page * size;
        Pageable pageable = PageRequest.of(page, size);

        // Handle case where page is beyond available data
        if (startIndex >= totalElements && totalElements > 0) {
            // Return empty page but with correct total elements
            return new PageImpl<>(List.of(), pageable, totalElements);
        }

        int endIndex = Math.min(startIndex + size, totalElements);

        List<PlanningHistory> pageContent;
        if (startIndex >= totalElements) {
            pageContent = List.of(); // Empty list for out-of-bounds pages
        } else {
            pageContent = history.subList(startIndex, endIndex);
        }

        List<PlanningHistoryDto> dtoContent =
                pageContent.stream().map(planningHistoryMapper::toDto).collect(Collectors.toList());

        return new PageImpl<>(dtoContent, pageable, totalElements);
    }

    private String truncateValue(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > 1000 ? value.substring(0, 1000) : value;
    }
}
