package com.sivalabs.ft.features.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sivalabs.ft.features.TestcontainersConfiguration;
import com.sivalabs.ft.features.domain.entities.Feature;
import com.sivalabs.ft.features.domain.entities.FeatureDependency;
import com.sivalabs.ft.features.domain.models.DependencyType;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Sql(scripts = {"/test-data.sql"})
class FeatureDependencyServiceTest {

    @Autowired
    private FeatureDependencyService featureDependencyService;

    @Autowired
    private FeatureRepository featureRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private FeatureDependencyRepository featureDependencyRepository;

    private Feature feature1;
    private Feature feature2;

    @BeforeEach
    void setUp() {
        // Get existing features from test data
        feature1 = featureRepository.findByCode("IDEA-1").orElseThrow();
        feature2 = featureRepository.findByCode("IDEA-2").orElseThrow();
    }

    @Test
    void testCreateFeatureDependency() {
        var cmd = new Commands.CreateFeatureDependencyCommand(
                feature1.getCode(), feature2.getCode(), "HARD", "Test notes", "test-user");

        featureDependencyService.createFeatureDependency(cmd);

        Optional<FeatureDependency> dependency = featureDependencyRepository.findByFeature_CodeAndDependsOnFeature_Code(
                feature1.getCode(), feature2.getCode());
        assertThat(dependency).isPresent();
        assertThat(dependency.get().getDependencyType()).isEqualTo(DependencyType.HARD);
        assertThat(dependency.get().getNotes()).isEqualTo("Test notes");
    }

    @Test
    void testUpdateFeatureDependency() {
        // First create a dependency
        var createCmd = new Commands.CreateFeatureDependencyCommand(
                feature1.getCode(), feature2.getCode(), "SOFT", "Initial notes", "test-user");
        featureDependencyService.createFeatureDependency(createCmd);

        // Then update it
        var updateCmd = new Commands.UpdateFeatureDependencyCommand(
                feature1.getCode(), feature2.getCode(), "HARD", "Updated notes", "test-user");
        featureDependencyService.updateFeatureDependency(updateCmd);

        Optional<FeatureDependency> dependency = featureDependencyRepository.findByFeature_CodeAndDependsOnFeature_Code(
                feature1.getCode(), feature2.getCode());
        assertThat(dependency).isPresent();
        assertThat(dependency.get().getDependencyType()).isEqualTo(DependencyType.HARD);
        assertThat(dependency.get().getNotes()).isEqualTo("Updated notes");
    }

    @Test
    void testDeleteFeatureDependency() {
        // First create a dependency
        var createCmd = new Commands.CreateFeatureDependencyCommand(
                feature1.getCode(), feature2.getCode(), "HARD", "Test notes", "test-user");
        featureDependencyService.createFeatureDependency(createCmd);

        // Verify it exists
        Optional<FeatureDependency> beforeDelete =
                featureDependencyRepository.findByFeature_CodeAndDependsOnFeature_Code(
                        feature1.getCode(), feature2.getCode());
        assertThat(beforeDelete).isPresent();

        // Delete it
        var deleteCmd =
                new Commands.DeleteFeatureDependencyCommand(feature1.getCode(), feature2.getCode(), "test-user");
        featureDependencyService.deleteFeatureDependency(deleteCmd);

        // Verify it's gone
        Optional<FeatureDependency> afterDelete =
                featureDependencyRepository.findByFeature_CodeAndDependsOnFeature_Code(
                        feature1.getCode(), feature2.getCode());
        assertThat(afterDelete).isEmpty();
    }

    @Test
    void testCreateDependencyWithNonExistentFeature() {
        var cmd = new Commands.CreateFeatureDependencyCommand(
                "NON-EXISTENT", feature2.getCode(), "HARD", "Test notes", "test-user");

        assertThatThrownBy(() -> featureDependencyService.createFeatureDependency(cmd))
                .isInstanceOf(RuntimeException.class); // Feature not found
    }
}
