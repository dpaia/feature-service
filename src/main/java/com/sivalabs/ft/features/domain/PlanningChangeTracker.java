package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.domain.Commands.DeleteFeatureCommand;
import com.sivalabs.ft.features.domain.Commands.UpdateFeatureCommand;
import com.sivalabs.ft.features.domain.entities.Feature;
import com.sivalabs.ft.features.domain.entities.Release;
import com.sivalabs.ft.features.domain.models.ChangeType;
import com.sivalabs.ft.features.domain.models.EntityType;
import com.sivalabs.ft.features.domain.models.FeaturePlanningStatus;
import com.sivalabs.ft.features.domain.models.FeatureStatus;
import com.sivalabs.ft.features.domain.models.ReleaseStatus;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class PlanningChangeTracker {

    private final PlanningHistoryService planningHistoryService;
    private final FeatureRepository featureRepository;
    private final ReleaseRepository releaseRepository;
    private final ThreadLocal<Map<String, Object>> oldValues = new ThreadLocal<>();

    public PlanningChangeTracker(
            PlanningHistoryService planningHistoryService,
            FeatureRepository featureRepository,
            ReleaseRepository releaseRepository) {
        this.planningHistoryService = planningHistoryService;
        this.featureRepository = featureRepository;
        this.releaseRepository = releaseRepository;
    }

    @Before("execution(* com.sivalabs.ft.features.domain.FeatureService.createFeature(..))")
    public void beforeFeatureCreate(JoinPoint joinPoint) {
        // No need to capture old values for create operation
    }

    @After("execution(* com.sivalabs.ft.features.domain.FeatureService.createFeature(..))")
    public void afterFeatureCreate(JoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        if (args.length > 0 && args[0] instanceof com.sivalabs.ft.features.domain.Commands.CreateFeatureCommand) {
            // The feature code is returned by the method, but we need to track the creation
            // We'll use a different approach - track in the service method itself
        }
    }

    @Before("execution(* com.sivalabs.ft.features.domain.FeatureService.updateFeature(..))")
    public void beforeFeatureUpdate(JoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        if (args.length > 0 && args[0] instanceof UpdateFeatureCommand cmd) {
            // Capture old values before update
            captureFeatureOldValues(cmd.code());
        }
    }

    @After("execution(* com.sivalabs.ft.features.domain.FeatureService.updateFeature(..))")
    public void afterFeatureUpdate(JoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        if (args.length > 0 && args[0] instanceof UpdateFeatureCommand cmd) {
            trackFeatureChanges(cmd);
        }
        oldValues.remove();
    }

    @Before("execution(* com.sivalabs.ft.features.domain.FeatureService.deleteFeature(..))")
    public void beforeFeatureDelete(JoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        if (args.length > 0 && args[0] instanceof DeleteFeatureCommand cmd) {
            captureFeatureOldValues(cmd.code());
        }
    }

    @After("execution(* com.sivalabs.ft.features.domain.FeatureService.deleteFeature(..))")
    public void afterFeatureDelete(JoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        if (args.length > 0 && args[0] instanceof DeleteFeatureCommand cmd) {
            Map<String, Object> oldVals = oldValues.get();
            if (oldVals != null) {
                Long entityId = (Long) oldVals.get("id");
                if (entityId != null) {
                    planningHistoryService.recordChange(
                            EntityType.FEATURE,
                            cmd.code(),
                            entityId,
                            ChangeType.DELETED,
                            null,
                            null,
                            null,
                            null,
                            cmd.deletedBy());
                }
            }
        }
        oldValues.remove();
    }

    @Before("execution(* com.sivalabs.ft.features.domain.ReleaseService.updateRelease(..))")
    public void beforeReleaseUpdate(JoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        if (args.length > 0 && args[0] instanceof com.sivalabs.ft.features.domain.Commands.UpdateReleaseCommand cmd) {
            captureReleaseOldValues(cmd.code());
        }
    }

    @After("execution(* com.sivalabs.ft.features.domain.ReleaseService.updateRelease(..))")
    public void afterReleaseUpdate(JoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        if (args.length > 0 && args[0] instanceof com.sivalabs.ft.features.domain.Commands.UpdateReleaseCommand cmd) {
            trackReleaseChanges(cmd);
        }
        oldValues.remove();
    }

    @After("execution(* com.sivalabs.ft.features.domain.ReleaseService.createRelease(..))")
    public void afterReleaseCreate(JoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        if (args.length > 0 && args[0] instanceof com.sivalabs.ft.features.domain.Commands.CreateReleaseCommand) {
            // Track creation - we'll need to get the generated code from return value
        }
    }

    private void captureFeatureOldValues(String code) {
        try {
            Feature feature = getFeatureByCode(code);
            if (feature != null) {
                Map<String, Object> values = new HashMap<>();
                values.put("id", feature.getId());
                values.put("title", feature.getTitle());
                values.put("description", feature.getDescription());
                values.put("status", feature.getStatus());
                values.put("assignedTo", feature.getAssignedTo());
                values.put(
                        "releaseCode",
                        feature.getRelease() != null ? feature.getRelease().getCode() : null);
                values.put("plannedCompletionAt", feature.getPlannedCompletionAt());
                values.put("actualCompletionAt", feature.getActualCompletionAt());
                values.put("featurePlanningStatus", feature.getFeaturePlanningStatus());
                values.put("featureOwner", feature.getFeatureOwner());
                values.put("blockageReason", feature.getBlockageReason());
                oldValues.set(values);
            }
        } catch (Exception e) {
            // Log error but don't fail the operation
            System.err.println("Error capturing feature old values: " + e.getMessage());
        }
    }

    private void captureReleaseOldValues(String code) {
        try {
            Release release = getReleaseByCode(code);
            if (release != null) {
                Map<String, Object> values = new HashMap<>();
                values.put("id", release.getId());
                values.put("description", release.getDescription());
                values.put("status", release.getStatus());
                values.put("releasedAt", release.getReleasedAt());
                values.put("plannedStartDate", release.getPlannedStartDate());
                values.put("plannedReleaseDate", release.getPlannedReleaseDate());
                values.put("actualReleaseDate", release.getActualReleaseDate());
                values.put("owner", release.getOwner());
                values.put("notes", release.getNotes());
                oldValues.set(values);
            }
        } catch (Exception e) {
            System.err.println("Error capturing release old values: " + e.getMessage());
        }
    }

    private void trackFeatureChanges(com.sivalabs.ft.features.domain.Commands.UpdateFeatureCommand cmd) {
        Map<String, Object> oldVals = oldValues.get();
        if (oldVals == null) return;

        Feature feature = getFeatureByCode(cmd.code());
        Long entityId = feature != null ? feature.getId() : (Long) oldVals.get("id");
        if (entityId == null) return;
        String changedBy = cmd.updatedBy();

        // Check each field for changes
        trackFieldChange(
                EntityType.FEATURE,
                cmd.code(),
                entityId,
                "title",
                (String) oldVals.get("title"),
                cmd.title(),
                changedBy);

        trackFieldChange(
                EntityType.FEATURE,
                cmd.code(),
                entityId,
                "description",
                (String) oldVals.get("description"),
                cmd.description(),
                changedBy);

        // Status change
        FeatureStatus oldStatus = (FeatureStatus) oldVals.get("status");
        if (oldStatus != cmd.status()) {
            planningHistoryService.recordChange(
                    EntityType.FEATURE,
                    cmd.code(),
                    entityId,
                    ChangeType.STATUS_CHANGED,
                    "status",
                    oldStatus != null ? oldStatus.toString() : null,
                    cmd.status() != null ? cmd.status().toString() : null,
                    null,
                    changedBy);
        }

        // Assignment change
        String oldAssignedTo = (String) oldVals.get("assignedTo");
        if (!java.util.Objects.equals(oldAssignedTo, cmd.assignedTo())) {
            planningHistoryService.recordChange(
                    EntityType.FEATURE,
                    cmd.code(),
                    entityId,
                    ChangeType.ASSIGNED,
                    "assignedTo",
                    oldAssignedTo,
                    cmd.assignedTo(),
                    null,
                    changedBy);
        }

        // Release change (move)
        String oldReleaseCode = (String) oldVals.get("releaseCode");
        if (!java.util.Objects.equals(oldReleaseCode, cmd.releaseCode())) {
            planningHistoryService.recordChange(
                    EntityType.FEATURE,
                    cmd.code(),
                    entityId,
                    ChangeType.MOVED,
                    "release",
                    oldReleaseCode,
                    cmd.releaseCode(),
                    null,
                    changedBy);
        }

        // Other planning fields
        trackFieldChange(
                EntityType.FEATURE,
                cmd.code(),
                entityId,
                "plannedCompletionAt",
                oldVals.get("plannedCompletionAt") != null
                        ? oldVals.get("plannedCompletionAt").toString()
                        : null,
                cmd.plannedCompletionAt() != null ? cmd.plannedCompletionAt().toString() : null,
                changedBy);

        trackFieldChange(
                EntityType.FEATURE,
                cmd.code(),
                entityId,
                "actualCompletionAt",
                oldVals.get("actualCompletionAt") != null
                        ? oldVals.get("actualCompletionAt").toString()
                        : null,
                cmd.actualCompletionAt() != null ? cmd.actualCompletionAt().toString() : null,
                changedBy);

        FeaturePlanningStatus oldPlanningStatus = (FeaturePlanningStatus) oldVals.get("featurePlanningStatus");
        if (oldPlanningStatus != cmd.featurePlanningStatus()) {
            planningHistoryService.recordChange(
                    EntityType.FEATURE,
                    cmd.code(),
                    entityId,
                    ChangeType.UPDATED,
                    "featurePlanningStatus",
                    oldPlanningStatus != null ? oldPlanningStatus.toString() : null,
                    cmd.featurePlanningStatus() != null
                            ? cmd.featurePlanningStatus().toString()
                            : null,
                    null,
                    changedBy);
        }

        trackFieldChange(
                EntityType.FEATURE,
                cmd.code(),
                entityId,
                "featureOwner",
                (String) oldVals.get("featureOwner"),
                cmd.featureOwner(),
                changedBy);

        trackFieldChange(
                EntityType.FEATURE,
                cmd.code(),
                entityId,
                "blockageReason",
                (String) oldVals.get("blockageReason"),
                cmd.blockageReason(),
                changedBy);
    }

    private void trackReleaseChanges(com.sivalabs.ft.features.domain.Commands.UpdateReleaseCommand cmd) {
        Map<String, Object> oldVals = oldValues.get();
        if (oldVals == null) return;

        Release release = getReleaseByCode(cmd.code());
        Long entityId = release != null ? release.getId() : (Long) oldVals.get("id");
        if (entityId == null) return;
        String changedBy = cmd.updatedBy();

        // Check each field for changes
        trackFieldChange(
                EntityType.RELEASE,
                cmd.code(),
                entityId,
                "description",
                (String) oldVals.get("description"),
                cmd.description(),
                changedBy);

        // Status change
        ReleaseStatus oldStatus = (ReleaseStatus) oldVals.get("status");
        if (oldStatus != cmd.status()) {
            planningHistoryService.recordChange(
                    EntityType.RELEASE,
                    cmd.code(),
                    entityId,
                    ChangeType.STATUS_CHANGED,
                    "status",
                    oldStatus != null ? oldStatus.toString() : null,
                    cmd.status() != null ? cmd.status().toString() : null,
                    null,
                    changedBy);
        }

        // Other fields
        trackFieldChange(
                EntityType.RELEASE,
                cmd.code(),
                entityId,
                "releasedAt",
                oldVals.get("releasedAt") != null ? oldVals.get("releasedAt").toString() : null,
                cmd.releasedAt() != null ? cmd.releasedAt().toString() : null,
                changedBy);

        trackFieldChange(
                EntityType.RELEASE,
                cmd.code(),
                entityId,
                "plannedStartDate",
                oldVals.get("plannedStartDate") != null
                        ? oldVals.get("plannedStartDate").toString()
                        : null,
                cmd.plannedStartDate() != null ? cmd.plannedStartDate().toString() : null,
                changedBy);

        trackFieldChange(
                EntityType.RELEASE,
                cmd.code(),
                entityId,
                "plannedReleaseDate",
                oldVals.get("plannedReleaseDate") != null
                        ? oldVals.get("plannedReleaseDate").toString()
                        : null,
                cmd.plannedReleaseDate() != null ? cmd.plannedReleaseDate().toString() : null,
                changedBy);

        trackFieldChange(
                EntityType.RELEASE,
                cmd.code(),
                entityId,
                "actualReleaseDate",
                oldVals.get("actualReleaseDate") != null
                        ? oldVals.get("actualReleaseDate").toString()
                        : null,
                cmd.actualReleaseDate() != null ? cmd.actualReleaseDate().toString() : null,
                changedBy);

        trackFieldChange(
                EntityType.RELEASE,
                cmd.code(),
                entityId,
                "owner",
                (String) oldVals.get("owner"),
                cmd.owner(),
                changedBy);

        trackFieldChange(
                EntityType.RELEASE,
                cmd.code(),
                entityId,
                "notes",
                (String) oldVals.get("notes"),
                cmd.notes(),
                changedBy);
    }

    private void trackFieldChange(
            EntityType entityType,
            String entityCode,
            Long entityId,
            String fieldName,
            String oldValue,
            String newValue,
            String changedBy) {
        if (!java.util.Objects.equals(oldValue, newValue)) {
            planningHistoryService.recordChange(
                    entityType,
                    entityCode,
                    entityId,
                    ChangeType.UPDATED,
                    fieldName,
                    oldValue,
                    newValue,
                    null,
                    changedBy);
        }
    }

    // These methods access the repositories to lookup entities by code
    private Feature getFeatureByCode(String code) {
        Optional<Feature> feature = featureRepository.findByCode(code);
        return feature.orElse(null);
    }

    private Release getReleaseByCode(String code) {
        Optional<Release> release = releaseRepository.findByCode(code);
        return release.orElse(null);
    }
}
