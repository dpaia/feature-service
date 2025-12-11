package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.domain.dtos.FeatureDto;
import com.sivalabs.ft.features.domain.entities.FeatureDependency;
import com.sivalabs.ft.features.domain.exceptions.BadRequestException;
import com.sivalabs.ft.features.domain.mappers.FeatureMapper;
import com.sivalabs.ft.features.domain.models.DependencyType;
import com.sivalabs.ft.features.domain.models.FeatureStatus;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeatureDependencyService {
    private final FeatureRepository featureRepository;
    private final FeatureDependencyRepository featureDependencyRepository;
    private final FeatureMapper featureMapper;

    FeatureDependencyService(
            FeatureRepository featureRepository,
            FeatureDependencyRepository featureDependencyRepository,
            FeatureMapper featureMapper) {
        this.featureRepository = featureRepository;
        this.featureDependencyRepository = featureDependencyRepository;
        this.featureMapper = featureMapper;
    }

    @Transactional
    public void createFeatureDependency(Commands.CreateFeatureDependencyCommand cmd) {
        var feature = featureRepository.findByCode(cmd.featureCode()).orElseThrow();
        var dependsOnFeature =
                featureRepository.findByCode(cmd.dependsOnFeatureCode()).orElseThrow();

        // Validate dependency type
        DependencyType dependencyType;
        try {
            dependencyType = DependencyType.valueOf(cmd.dependencyType());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(
                    "Invalid dependency type: " + cmd.dependencyType() + ". Supported types are: HARD, SOFT, OPTIONAL");
        }

        var dependency = new FeatureDependency();
        dependency.setFeature(feature);
        dependency.setDependsOnFeature(dependsOnFeature);
        dependency.setDependencyType(dependencyType);
        dependency.setNotes(cmd.notes());
        dependency.setCreatedAt(Instant.now());
        featureDependencyRepository.save(dependency);
    }

    @Transactional
    public void updateFeatureDependency(Commands.UpdateFeatureDependencyCommand cmd) {
        var dependency = featureDependencyRepository
                .findByFeature_CodeAndDependsOnFeature_Code(cmd.featureCode(), cmd.dependsOnFeatureCode())
                .orElseThrow();

        // Validate dependency type
        DependencyType dependencyType;
        try {
            dependencyType = DependencyType.valueOf(cmd.dependencyType());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(
                    "Invalid dependency type: " + cmd.dependencyType() + ". Supported types are: HARD, SOFT, OPTIONAL");
        }

        dependency.setDependencyType(dependencyType);
        dependency.setNotes(cmd.notes());
        featureDependencyRepository.save(dependency);
    }

    @Transactional
    public void deleteFeatureDependency(Commands.DeleteFeatureDependencyCommand cmd) {
        var dependency = featureDependencyRepository
                .findByFeature_CodeAndDependsOnFeature_Code(cmd.featureCode(), cmd.dependsOnFeatureCode())
                .orElseThrow();

        featureDependencyRepository.delete(dependency);
    }

    /**
     * Get all dependencies for a given feature with additional filtering
     */
    @Transactional(readOnly = true)
    public List<FeatureDto> getDependencies(
            String featureCode, String productCode, String releaseCode, FeatureStatus status) {
        // Feature existence is checked in controller to avoid transaction rollback issues

        // Use the new repository method that handles all filtering in the database
        List<FeatureDependency> dependencies = featureDependencyRepository.findDependenciesWithFullFiltering(
                featureCode, productCode, releaseCode, status);

        // Extract the features that this feature depends on
        return dependencies.stream()
                .map(dep -> featureMapper.toDto(dep.getDependsOnFeature()))
                .collect(Collectors.toList());
    }

    /**
     * Get all dependents for a given feature (features that depend on this one)
     */
    @Transactional(readOnly = true)
    public List<FeatureDto> getDependents(
            String featureCode, String productCode, String releaseCode, FeatureStatus status) {
        // Feature existence is checked in controller to avoid transaction rollback issues

        List<FeatureDependency> dependents;

        if (releaseCode != null) {
            dependents = featureDependencyRepository.findDependentsByRelease(featureCode, releaseCode);
        } else {
            dependents = featureDependencyRepository.findDependentsWithFiltering(featureCode, productCode, status);
        }

        // Extract the features that depend on this feature
        return dependents.stream()
                .map(dep -> featureMapper.toDto(dep.getFeature()))
                .collect(Collectors.toList());
    }

    /**
     * Perform comprehensive impact analysis to find all features affected by a change
     * This includes both direct and indirect dependents
     */
    @Transactional(readOnly = true)
    public List<FeatureDto> getImpactAnalysis(
            String featureCode, String productCode, String releaseCode, FeatureStatus status) {
        // Feature existence is checked in controller to avoid transaction rollback issues

        Set<String> processedFeatures = new HashSet<>();
        Set<String> affectedFeatureCodes = new HashSet<>(); // Use Set to avoid duplicates
        Queue<String> featuresToProcess = new LinkedList<>();

        featuresToProcess.add(featureCode);
        processedFeatures.add(featureCode);

        while (!featuresToProcess.isEmpty()) {
            String currentFeatureCode = featuresToProcess.poll();

            // Get direct dependents of current feature WITHOUT filtering to maintain complete dependency chain
            List<FeatureDependency> directDependents =
                    featureDependencyRepository.findDependentsWithFiltering(currentFeatureCode, null, null);

            for (FeatureDependency dependent : directDependents) {
                String dependentFeatureCode = dependent.getFeature().getCode();

                // Don't include the original feature in the results (avoid self-reference in circular dependencies)
                if (!dependentFeatureCode.equals(featureCode)) {
                    // Add to results - just the feature code to avoid duplicates
                    affectedFeatureCodes.add(dependentFeatureCode);
                }

                // Add to processing queue if not already processed (to find indirect dependents)
                if (!processedFeatures.contains(dependentFeatureCode)) {
                    featuresToProcess.add(dependentFeatureCode);
                    processedFeatures.add(dependentFeatureCode);
                }
            }
        }

        // Convert feature codes to FeatureDto objects and apply filtering to final result
        return affectedFeatureCodes.stream()
                .map(code -> featureRepository
                        .findByCode(code)
                        .map(featureMapper::toDto)
                        .orElse(null))
                .filter(Objects::nonNull)
                .filter(featureDto -> matchesFilters(featureDto, productCode, releaseCode, status))
                .collect(Collectors.toList());
    }

    private boolean matchesFilters(FeatureDto feature, String productCode, String releaseCode, FeatureStatus status) {
        // For productCode filtering, we need to get the actual Feature entity to access the product
        if (productCode != null) {
            var featureEntity = featureRepository.findByCode(feature.code()).orElse(null);
            if (featureEntity == null
                    || featureEntity.getProduct() == null
                    || !productCode.equals(featureEntity.getProduct().getCode())) {
                return false;
            }
        }
        if (releaseCode != null && !releaseCode.equals(feature.releaseCode())) {
            return false;
        }
        if (status != null && !status.equals(feature.status())) {
            return false;
        }
        return true;
    }

    /**
     * Check for circular dependencies starting from a given feature
     */
    @Transactional(readOnly = true)
    public boolean hasCircularDependency(String featureCode) {
        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();

        return hasCircularDependencyHelper(featureCode, visited, recursionStack);
    }

    private boolean hasCircularDependencyHelper(String featureCode, Set<String> visited, Set<String> recursionStack) {
        if (recursionStack.contains(featureCode)) {
            return true; // Circular dependency found
        }

        if (visited.contains(featureCode)) {
            return false; // Already processed this path
        }

        visited.add(featureCode);
        recursionStack.add(featureCode);

        // Get all features this feature depends on
        List<FeatureDependency> dependencies = featureDependencyRepository.findByFeature_Code(featureCode);

        for (FeatureDependency dependency : dependencies) {
            String dependsOnFeatureCode = dependency.getDependsOnFeature().getCode();
            if (hasCircularDependencyHelper(dependsOnFeatureCode, visited, recursionStack)) {
                return true;
            }
        }

        recursionStack.remove(featureCode);
        return false;
    }
}
