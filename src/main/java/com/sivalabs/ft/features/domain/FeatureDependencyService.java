package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.domain.entities.FeatureDependency;
import com.sivalabs.ft.features.domain.exceptions.BadRequestException;
import com.sivalabs.ft.features.domain.models.DependencyType;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeatureDependencyService {
    private final FeatureRepository featureRepository;
    private final FeatureDependencyRepository featureDependencyRepository;

    FeatureDependencyService(
            FeatureRepository featureRepository, FeatureDependencyRepository featureDependencyRepository) {
        this.featureRepository = featureRepository;
        this.featureDependencyRepository = featureDependencyRepository;
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
}
