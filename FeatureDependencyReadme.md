**Objective**
Define the data structure for feature dependencies and ensure they are correctly stored in the database with referential integrity.

**Requirements**

**Feature Dependency Entity**: Create a database entity or model with the fields: id,  **feature (JPA @ManyToOne relationship to dependent Feature entity), dependsOnFeature (JPA @ManyToOne relationship to dependency Feature entity using feature.code for joining)**, dependencyType (hard/soft/optional), createdAt, and notes.

**Relationships**: Implement the one-to-many relationship allowing any feature to have zero or more dependencies **using JPA relationships with bidirectional @OneToMany mappings in Feature entity**.

**Database Schema**: Ensure the database schema is updated to store dependencies and enforces referential integrity (e.g., a dependency cannot be created for a non-existent featureCode) **through foreign key constraints on feature_code and depends_on_feature_code columns referencing features.code**.

**CRUD Repository**: Implement a FeatureDependencyRepository that retrieves dependencies by feature code **using Spring Data JPA nested property syntax (e.g., findByFeature_Code)**.

**Test Coverage**

- Unit tests for the FeatureDependency entity/model.
- Integration tests for database persistence (create, read, update, delete).
- Tests to ensure database constraints (like foreign keys) prevent invalid data entry **and enforce unique constraints preventing duplicate dependencies and self-dependencies**.

**Acceptance Criteria**

- The FeatureDependency entity is correctly defined in the codebase and migrated to the database **using JPA @ManyToOne relationships to Feature entities instead of string fields**.
- Dependencies can be successfully saved to, and retrieved from, the database **through JPA entity relationships**.
- The system prevents the creation of dependencies that reference non-existent features **through database foreign key constraints and JPA relationship validation**.

## ● Understanding the Architecture

This is a Feature Tracker - a system for managing products and their features during development. The project solves these tasks:

1. **Product line management** - multiple products (e.g., IntelliJ IDEA, WebStorm)
2. **Release planning** - organizing features by releases  
3. **Feature tracking** - from idea to implementation
4. **Dependency management** - understanding relationships between features

### Understanding the Entities

**Feature**

This is a business requirement or functionality that needs to be developed:
- `code` - unique logical identifier (e.g., "IDEA-1234", "SEARCH-EVERYWHERE")
- `title` - feature name ("Smart Code Completion")
- `description` - detailed description
- `status` - development stage (NEW, IN_PROGRESS, DONE, etc.)
- Linked to Product and can be in a Release

**FeatureDependency**

This is a relationship between features, indicating that one feature depends on another:
- `featureCode` - code of the dependent feature (e.g., "SMART-SEARCH")
- `dependsOnFeatureCode` - code of the feature being depended on (e.g., "INDEXING-ENGINE")
- `dependencyType` - type of dependency:
  - **HARD** - critical, feature won't work without it
  - **SOFT** - desirable, improves functionality
  - **OPTIONAL** - can be implemented independently

### Relationships Between Entities

```
Feature "SMART-SEARCH" depends on Feature "INDEXING-ENGINE" (HARD)
         ↓ depends on ↓
Feature "INDEXING-ENGINE"
```

Logical names (codes) are stored in the database, not code snippets! For example:
- `featureCode`: "IDEA-2024-REFACTORING"
- `dependsOnFeatureCode`: "IDEA-2024-AST-PARSER"

### Relationship Architecture

**In FeatureDependency Entity:**
- JPA relationship fields (`feature`, `dependsOnFeature`) - ManyToOne relationships to Feature entities
- No separate string fields - uses JPA relationships directly for both navigation and queries

**In Feature Entity:**
- `dependencies` - list of this feature's dependencies
- `dependentFeatures` - list of features depending on this one

### Practical Example

```sql
-- Feature "Smart Completion" depends on "Language Parser"
INSERT INTO feature_dependencies VALUES
(1, 'IDEA-SMART-COMPLETION', 'IDEA-LANG-PARSER', 'HARD', 'Needs AST for analysis');

-- Feature "Code Folding" also depends on "Language Parser"  
INSERT INTO feature_dependencies VALUES
(2, 'IDEA-CODE-FOLDING', 'IDEA-LANG-PARSER', 'SOFT', 'Better folding with syntax');
```

**Result:** Smart Completion cannot be released without Language Parser, but Code Folding can be released without it (though it would be worse).

The system allows for release planning, understanding task criticality, and development order.

---

## ● JPA Relationship Solution

**FeatureDependency → Feature (Many-to-One)**

```java
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "feature_code", referencedColumnName = "code", nullable = false)
private Feature feature;  // The feature that has dependencies

@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "depends_on_feature_code", referencedColumnName = "code", nullable = false)
private Feature dependsOnFeature;  // The feature being depended on
```

**Feature → FeatureDependency (One-to-Many)**

```java
// One-to-many relationship for feature dependencies
@OneToMany(mappedBy = "feature", fetch = FetchType.LAZY, orphanRemoval = true)
private List<FeatureDependency> dependencies = new ArrayList<>();

// Features that depend on this feature
@OneToMany(mappedBy = "dependsOnFeature", fetch = FetchType.LAZY, orphanRemoval = true)
private List<FeatureDependency> dependentFeatures = new ArrayList<>();
```

### Practical Example:

```
-- Feature "SMART-COMPLETION" has 3 dependencies:
feature_dependencies:
id | feature_code        | depends_on_feature_code | type
1  | SMART-COMPLETION    | LANG-PARSER            | HARD
2  | SMART-COMPLETION    | INDEXING-ENGINE        | HARD
3  | SMART-COMPLETION    | USER-PREFERENCES       | SOFT
```

**Result:**
- 3 FeatureDependency records
- Each linked to one Feature ("SMART-COMPLETION")
- But this Feature has 3 dependencies in its dependencies list

### Architectural Solution:

This is a **pure JPA relationship approach**:
- **JPA relationship fields** (`feature`, `dependsOnFeature`) - direct relationships to Feature entities
- **Database constraints** - for referential integrity via foreign keys
- **Helper methods** - for synchronizing bidirectional relationships
- **Spring Data JPA queries** - using nested property paths (e.g., `findByFeature_Code()`)

The field names `feature` and `dependsOnFeature` represent JPA relationships to Feature entities, and string codes can be extracted by feature.code and dependsOnFeature.code

## Implementation Details

### Key Files

- [`FeatureDependency.java`](src/main/java/com/sivalabs/ft/features/domain/entities/FeatureDependency.java) - Main entity with JPA relationships
- [`Feature.java`](src/main/java/com/sivalabs/ft/features/domain/entities/Feature.java) - Updated with bidirectional relationships
- [`FeatureDependencyRepository.java`](src/main/java/com/sivalabs/ft/features/domain/FeatureDependencyRepository.java) - Repository with core query methods
- [`V5__create_feature_dependencies_table.sql`](src/main/resources/db/migration/V5__create_feature_dependencies_table.sql) - Database migration with constraints

### Repository Methods

The `FeatureDependencyRepository` provides essential query methods:

```java
// Find all dependencies of a specific feature
List<FeatureDependency> findByFeature_Code(String featureCode);

// Find all features that depend on a specific feature
List<FeatureDependency> findByDependsOnFeature_Code(String dependsOnFeatureCode);

// Find specific dependency between two features
Optional<FeatureDependency> findByFeature_CodeAndDependsOnFeature_Code(
    String featureCode, String dependsOnFeatureCode);
```

### Database Constraints

The database schema enforces data integrity through:

- **Foreign Key Constraints**: Prevent dependencies on non-existent features
- **Unique Constraint**: Prevent duplicate dependencies between same features
- **Check Constraint**: Prevent self-dependencies (feature depending on itself)
- **Enum Constraint**: Ensure dependency type is HARD, SOFT, or OPTIONAL

### Test Coverage

- **Unit Tests**: [`FeatureDependencyTest.java`](src/test/java/com/sivalabs/ft/features/domain/entities/FeatureDependencyTest.java) - Entity behavior
- **Integration Tests**: [`FeatureDependencyRepositoryTest.java`](src/test/java/com/sivalabs/ft/features/domain/FeatureDependencyRepositoryTest.java) - CRUD operations and queries
- **Integrity Tests**: [`FeatureDependencyIntegrityTest.java`](src/test/java/com/sivalabs/ft/features/domain/FeatureDependencyIntegrityTest.java) - Database constraints and referential integrity
