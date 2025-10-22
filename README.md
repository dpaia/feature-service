# feature-service
The feature-service microservice manages products, releases and features.

## TechStack
* Java, Spring Boot
* PostgreSQL, Flyway, Spring Data JPA
* Spring Security OAuth 2
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

## Asynchronous Event Handling

The feature-service uses asynchronous event processing for feature-related events. This improves system responsiveness and scalability by processing events in the background.

### How It Works

1. When a feature is created, updated, or deleted, an event is published
2. Event listeners annotated with `@Async` process these events asynchronously
3. A dedicated thread pool handles the asynchronous processing
4. The ApplicationEventMulticaster routes events to the appropriate listeners

### Configuration

Thread pool settings can be configured in `application.properties`:

```properties
# Core number of threads in the thread pool
ft.async.core-pool-size=2

# Maximum number of threads in the thread pool
ft.async.max-pool-size=5

# Queue capacity for tasks before blocking
ft.async.queue-capacity=100

# Prefix for thread names in the pool
ft.async.thread-name-prefix=feature-async-
```

### Tuning Guidelines

- **Core Pool Size**: Set based on the average number of concurrent events you expect
- **Max Pool Size**: Set higher than core pool size to handle bursts of activity
- **Queue Capacity**: Determines how many tasks can wait when all threads are busy
- **Thread Name Prefix**: Useful for identifying threads in logs and monitoring tools

For high-throughput environments, consider increasing both pool sizes and queue capacity.

### Observing Async Behavior

You can observe the asynchronous processing through application logs. When a feature event is processed, the log will include the thread name:

```
INFO c.s.f.f.d.e.FeatureEventListener : Feature created event received - ID: 123, Name: Example Feature, Thread: feature-async-1
```

The thread name (e.g., `feature-async-1`) confirms that the event is being processed by the dedicated thread pool rather than the main application thread.
