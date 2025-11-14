# feature-service
The feature-service microservice manages products, releases and features.

## TechStack
* Java, Spring Boot
* PostgreSQL, Flyway, Spring Data JPA
* Spring Security OAuth 2
* RabbitMQ (External Event Relay)
* Maven, JUnit 5, Testcontainers

## Prerequisites
* JDK 21 or later
* Docker ([installation instructions](https://docs.docker.com/engine/install/))
* [IntelliJ IDEA](https://www.jetbrains.com/idea/)
* PostgreSQL and Keycloak
* RabbitMQ (for external event propagation)
 
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

## External Event Propagation with RabbitMQ

The feature-service publishes feature events (created, updated, deleted) to RabbitMQ for external system integration.

### RabbitMQ Setup

#### Using Docker

```shell
docker run -d --name rabbitmq \
  -p 5672:5672 \
  -p 15672:15672 \
  rabbitmq:4.0-management
```

Access RabbitMQ Management UI at: http://localhost:15672
- Username: `guest`
- Password: `guest`

#### Using Docker Compose

Add to your `docker-compose.yml`:

```yaml
services:
  rabbitmq:
    image: rabbitmq:4.0-management
    ports:
      - "5672:5672"
      - "15672:15672"
    environment:
      RABBITMQ_DEFAULT_USER: guest
      RABBITMQ_DEFAULT_PASS: guest
```

### Configuration

Set the following environment variables or update `application.properties`:

```properties
# RabbitMQ Connection
RABBITMQ_HOST=localhost
RABBITMQ_PORT=5672
RABBITMQ_USERNAME=guest
RABBITMQ_PASSWORD=guest

# RabbitMQ Exchanges and Queues (pre-configured)
ft.rabbitmq.exchange=feature-events-exchange
ft.rabbitmq.queue=feature-events-queue
ft.rabbitmq.routing-key.created=feature.created
ft.rabbitmq.routing-key.updated=feature.updated
ft.rabbitmq.routing-key.deleted=feature.deleted
```

### Event Flow

```
Feature CRUD Operation
    ↓
FeatureService
    ↓
EventPublisher
    ↓
Spring Application Events
    ↓
FeatureEventListener
    ↓
RabbitMQEventPublisher → RabbitMQ Exchange → Queue
```

### Features

- **Event Publishing**: Feature events published to RabbitMQ automatically
- **Transaction Safety**: Events published only after successful database commit
- **Retry Mechanism**: Automatic retry with exponential backoff (3 attempts max)
- **Dead Letter Queue**: Failed messages routed to DLQ for manual inspection
- **Async Processing**: Non-blocking event relay to RabbitMQ
- **Error Handling**: Comprehensive logging and exception handling

### Monitoring

- **RabbitMQ Management UI**: http://localhost:15672
  - View queues and message rates
  - Inspect message content
  - Monitor DLQ for failed messages
  - Check connection status

