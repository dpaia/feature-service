# feature-service
The feature-service microservice manages products, releases and features.

## TechStack
* Java, Spring Boot
* PostgreSQL, Flyway, Spring Data JPA
* Spring Security OAuth 2
* Maven, JUnit 5, Testcontainers

## Application Lifecycle
The application listens to Spring context lifecycle events to validate critical dependencies at startup and to
flush Kafka safely at shutdown.

Startup (ContextRefreshedEvent):
* Verifies the PostgreSQL connection is usable.
* Verifies Kafka connectivity and that the three configured topics exist (`new_features`, `updated_features`,
  `deleted_features`). Topics are not auto-created.
* Warms up the Kafka producer by fetching metadata for the configured topics.

Shutdown (ContextClosedEvent):
* Attempts to flush pending Kafka messages before exit.
* Flush timeout is capped at 10 seconds and also bounded by the configurable total shutdown timeout.
* Total shutdown timeout is controlled by `ft.shutdown.timeout-seconds` (default 30 seconds).
* If flush fails or times out, the error is logged and shutdown continues. Database connections are closed
  by Spring Boot.

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
