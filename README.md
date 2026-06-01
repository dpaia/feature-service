# feature-service
The feature-service microservice manages products, releases and features.

## TechStack
* Java, Spring Boot
* PostgreSQL, Flyway, Spring Data JPA
* Spring Security OAuth 2
* Maven, JUnit 5, Testcontainers

## Prerequisites
* JDK 24 or later
* Docker ([installation instructions](https://docs.docker.com/engine/install/))
* [IntelliJ IDEA](https://www.jetbrains.com/idea/)
* PostgreSQL and Keycloak 
 
Refer [docker-compose based infra setup](https://github.com/feature-tracker/docker-infra) for running dependent services.

## How to get started?

```shell
$ git clone https://github.com/feature-tracker/feature-service.git
$ cd feature-service

# Run tests
$ ./mvnw verify

# Format code
$ ./mvnw spotless:apply

# Run application
# Once the dependent services (PostgreSQL, Keycloak, etc) are started, 
# you can run/debug FeatureServiceApplication.java from your IDE.
```

## Conditional Event Processing

The feature-service uses conditional event processing to selectively handle events based on specific criteria. This allows for more fine-grained control over event handling and enables different business workflows based on event attributes.

### Role-Based Event Processing

One key implementation is role-based event processing, where certain event handlers only process events if they were created by users with specific roles:

```java
@EventListener(condition = "#event.creatorRole == 'ADMIN'")
public void handleAdminFeatureCreatedEvent(FeatureCreatedEvent event) {
    // This method only executes for features created by administrators
    logger.info("Admin feature created event received - ID: {}, Name: {}, Creator: {}, Role: {}", 
            event.id(), event.title(), event.createdBy(), event.creatorRole());
    
    // Special processing for admin-created features
}
```

### Business Use Cases

Conditional event processing enables several important business workflows:

1. **Role-Based Processing**: Different processing logic based on the creator's role
2. **Approval Workflows**: Automatic approval for admin-created features, manual approval for others
3. **Compliance and Auditing**: Special logging or validation for features created by administrators
4. **Notifications**: Different notification strategies based on who created the feature

### Implementation Details

The implementation includes:

1. **Event Data**: The `FeatureCreatedEvent` includes the creator's role
2. **Conditional Annotation**: The `@EventListener` annotation uses SpEL expressions to filter events
3. **Role Determination**: The creator's role is determined when the event is published

### Testing Conditional Event Processing

Testing conditional event processing requires:

1. **Unit Tests**: Verify that event handlers process events correctly based on conditions
2. **Integration Tests**: Confirm that the Spring event mechanism correctly applies conditions

For examples, see `FeatureEventListenerTest.java`.
