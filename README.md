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

## Feature Event Async Processing

Feature-created application events are delivered through a dedicated
`ThreadPoolTaskExecutor`. Tune the executor with `ft.async.core-pool-size`,
`ft.async.max-pool-size`, `ft.async.queue-capacity`, and
`ft.async.thread-name-prefix`. Listener logs include the processing thread name,
which can be used to confirm that feature event handling runs outside the
request thread.
