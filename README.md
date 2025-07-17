# feature-service
The feature-service microservice manages products, releases and features.

## TechStack
* Java, Spring Boot
* PostgreSQL, Flyway, Spring Data JPA
* Spring Security OAuth 2
* Spring Cache with Ehcache
* Maven, JUnit 5, Testcontainers

## Prerequisites
* JDK 21 or later
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

## Cache Configuration

The application uses Ehcache as the caching provider via Spring Cache abstraction to improve query performance. Two caches are configured:

1. **featureById**: Caches Feature objects by their ID
2. **featuresByStatus**: Caches lists of Features by their status

### Cache Configuration Details

- Cache configuration is defined in `src/main/resources/ehcache.xml`
- Each cache has a TTL (Time-To-Live) of 30 minutes
- Each cache can store up to 1000 entries
- Cache statistics are enabled for monitoring

### Cached Methods

The following methods in `FeatureService` are cached:

- `findById(Long id)`: Retrieves a Feature by its ID
- `findByStatus(FeatureStatus status)`: Retrieves Features by their status

### Monitoring Cache Usage

Cache statistics can be monitored through Spring Boot Actuator endpoints:

```shell
# Get all available metrics
curl http://localhost:8081/actuator/metrics

# Get cache statistics for featureById cache
curl http://localhost:8081/actuator/metrics/cache.gets?tag=name:featureById

# Get cache statistics for featuresByStatus cache
curl http://localhost:8081/actuator/metrics/cache.gets?tag=name:featuresByStatus
```
