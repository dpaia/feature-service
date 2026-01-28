# Unit Tests Implementation Requirements for ApplicationLifecycleListener

## Overview
This document details the white-box implementation specifications extracted from `ApplicationLifecycleListenerUnitTest.java`. These requirements transform the unit tests from black-box to white-box specifications by providing exact implementation details, method signatures, exception handling logic, and verification requirements.

---

## 1. Class Architecture

### 1.1 ApplicationLifecycleListener Class Definition
- **Package**: `com.sivalabs.ft.features.config`
- **Dependencies**: Injected via constructor
- **Spring Component**: Must be annotated with `@Component` or similar to be registered as a Spring bean
- **Event Listeners**: Must implement Spring event listener methods
- **Logging**: Uses SLF4J Logger (recommended: `private static final Logger log = LoggerFactory.getLogger(ApplicationLifecycleListener.class);`)

### 1.2 Constructor Signature
```java
public ApplicationLifecycleListener(
    DataSource dataSource,
    KafkaTemplate<String, Object> kafkaTemplate,
    AdminClient kafkaAdminClient,
    ApplicationProperties applicationProperties
)
```

**Constructor Behavior**:
- Store all four parameters as instance fields
- All parameters are required (non-null)

---

## 2. ApplicationProperties Structure

### 2.1 ApplicationProperties Class
Must be a Spring `@ConfigurationProperties` class containing nested classes:

#### EventsProperties Nested Class
```java
public static class EventsProperties {
    private String newFeaturesTopic;
    private String updatedFeaturesTopic;
    private String deletedFeaturesTopic;
    
    public EventsProperties(String newFeaturesTopic, String updatedFeaturesTopic, String deletedFeaturesTopic) {
        this.newFeaturesTopic = newFeaturesTopic;
        this.updatedFeaturesTopic = updatedFeaturesTopic;
        this.deletedFeaturesTopic = deletedFeaturesTopic;
    }
}
```
- `newFeaturesTopic`: Topic name for new features (e.g., "new_features")
- `updatedFeaturesTopic`: Topic name for updated features (e.g., "updated_features")
- `deletedFeaturesTopic`: Topic name for deleted features (e.g., "deleted_features")

#### LifecycleProperties Nested Class
```java
public static class LifecycleProperties {
    private boolean enabled;
    private long startupTimeout;
    private long kafkaCheckTimeout;
    
    public LifecycleProperties(boolean enabled, long startupTimeout, long kafkaCheckTimeout) {
        this.enabled = enabled;
        this.startupTimeout = startupTimeout;
        this.kafkaCheckTimeout = kafkaCheckTimeout;
    }
}
```
- `enabled`: Boolean flag to enable/disable lifecycle management
- `startupTimeout`: Total shutdown timeout in milliseconds (e.g., 30000L = 30 seconds) - USED ONLY IN SHUTDOWN
- `kafkaCheckTimeout`: Kafka flush timeout in milliseconds (e.g., 10000L = 10 seconds) - USED ONLY IN SHUTDOWN

---

## 3. Application Startup Behavior

### 3.1 Method Signature and Registration
```java
@EventListener
public void onApplicationStartup(ContextRefreshedEvent event)
```

**Alternative**: Can implement `ApplicationListener<ContextRefreshedEvent>` with `onApplicationEvent()` method

### 3.2 Execution Flow
The method must:
1. **Check database connectivity first** (must happen before Kafka check)
2. **Then check Kafka connectivity** (only if database is available)
3. **Throw RuntimeException on any failure**
4. **Not catch any exceptions** (allow them to propagate to Spring)

### 3.3 Database Connectivity Check

#### Implementation Steps
1. Call `dataSource.getConnection()` to obtain a connection
2. Call `connection.isValid(int timeout)` to validate the connection
3. Close the connection (if obtained successfully)
4. Catch `SQLException` and wrap in RuntimeException

#### Error Handling - SQLException from getConnection()
**When**: `dataSource.getConnection()` throws `SQLException`

**Exception Message Format**:
```
"Application startup failed: Database unavailable: {sqlException.getMessage()}"
```

**Wrapping Logic**:
```java
catch (SQLException e) {
    throw new RuntimeException("Application startup failed: Database unavailable: " + e.getMessage(), e);
}
```

**Test Case Examples**:
- Input: `SQLException("Connection refused")`
- Output: `RuntimeException("Application startup failed: Database unavailable: Connection refused")`

- Input: `SQLException("Connection timeout")`
- Output: `RuntimeException("Application startup failed: Database unavailable: Connection timeout")`

#### Connection Validation Details
- Call `Connection.isValid(int timeout)` with any timeout value (not verified by tests)
- If connection is invalid, also handle as database connectivity failure
- Close connection after validation

### 3.4 Kafka Connectivity Check

#### Implementation Steps
1. Collect topic names from `ApplicationProperties` EventsProperties
2. Call `kafkaAdminClient.describeTopics(Collection<String> topicNames)`
3. Get result as `DescribeTopicsResult`
4. Call `describeTopicsResult.allTopicNames()` to retrieve a `KafkaFuture`
5. Call `kafkaFuture.get()` to wait for result (with timeout)
6. Catch exceptions and wrap in RuntimeException

#### Error Handling - TimeoutException
**When**: `kafkaFuture.get(...)` throws `TimeoutException`

**Exception Message Format**:
```
"Application startup failed: Kafka unavailable: {exception.getMessage()}"
```

**Wrapping Logic**:
```java
catch (TimeoutException e) {
    throw new RuntimeException("Application startup failed: Kafka unavailable: " + e.getMessage(), e);
}
```

**Test Case**:
- Input: `TimeoutException("Connection timeout")`
- Output: `RuntimeException("Application startup failed: Kafka unavailable: Connection timeout")`

#### Error Handling - InterruptedException
**When**: `kafkaFuture.get(...)` throws `InterruptedException`

**Exception Message Format**: Must contain "Kafka unavailable"

**Wrapping Logic**:
```java
catch (InterruptedException e) {
    throw new RuntimeException("Application startup failed: Kafka unavailable: " + e.getMessage(), e);
}
```

**Test Case**:
- Input: `InterruptedException("Thread interrupted")`
- Output: `RuntimeException("Application startup failed: Kafka unavailable: Thread interrupted")`

#### Error Handling - ExecutionException
**When**: `kafkaFuture.get(...)` throws `ExecutionException`

**Handling**: Wrap and propagate as RuntimeException with "Kafka unavailable" message

#### Kafka Topic Names
The implementation should pass all three topic names from `ApplicationProperties.EventsProperties`:
- newFeaturesTopic
- updatedFeaturesTopic
- deletedFeaturesTopic

**Verification**: Tests verify that `kafkaAdminClient.describeTopics(anyCollection())` is called exactly once

---

## 4. Application Shutdown Behavior

### 4.1 Method Signature and Registration
```java
@EventListener
public void onApplicationShutdown(ContextClosedEvent event)
```

**Alternative**: Can implement `ApplicationListener<ContextClosedEvent>` with `onApplicationEvent()` method

### 4.2 Execution Flow
The method must:
1. **Check if lifecycle is enabled** in ApplicationProperties
2. **Only proceed with Kafka flush if enabled**
3. **Gracefully handle all exceptions during shutdown**
4. **Never throw exceptions** (shutdown must complete silently even on error)
5. **Complete shutdown within 30 seconds total timeout**

### 4.3 Shutdown Timeouts

- **Total Shutdown Timeout**: 30 seconds (`LifecycleProperties.startupTimeout` = 30000L)
- **Kafka Flush Timeout**: 10 seconds (`LifecycleProperties.kafkaCheckTimeout` = 10000L)

The Kafka flush operation must be implemented with a 10-second timeout. The entire shutdown process must complete within the 30-second total timeout.

### 4.4 Lifecycle Configuration Check

**When `LifecycleProperties.enabled == false`**:
- Do NOT call `kafkaTemplate.flush()`
- Complete shutdown immediately
- No exceptions should be thrown
- Method should return void without error

**Verification**: `verify(kafkaTemplate, times(0)).flush()`

**Implementation**:
```java
if (!applicationProperties.getLifecycleProperties().isEnabled()) {
    return;
}
```

**When `LifecycleProperties.enabled == false`**:
- Do NOT call `kafkaTemplate.flush()`
- Complete shutdown immediately
- No exceptions should be thrown

**Verification**: `verify(kafkaTemplate, times(0)).flush()`

### 4.5 Kafka Flush Operation

**When `LifecycleProperties.enabled == true`**:
- Call `kafkaTemplate.flush()` exactly once
- Must complete within 10-second timeout
- Wrap call in try-catch to handle exceptions

**Verification**: `verify(kafkaTemplate, times(1)).flush()`

**Implementation**:
```java
try {
    kafkaTemplate.flush();
} catch (Exception e) {
    // Handle exceptions gracefully - see section 4.6
}
```

### 4.6 Exception Handling During Shutdown

The method must catch and gracefully handle ALL exceptions during Kafka flush without propagating them or failing the shutdown process. The method must **never throw exceptions**.

#### NullPointerException Handling
- **Cause**: `kafkaTemplate.flush()` throws `NullPointerException("Kafka template is null")`
- **Behavior**: Catch exception and continue gracefully
- **Verification**: `verify(kafkaTemplate, times(1)).flush()` - flush should still be called
- **Result**: Shutdown completes without throwing exception
- **Logging**: Should log the exception (recommended)

**Implementation**:
```java
catch (NullPointerException e) {
    log.warn("NullPointerException during Kafka flush: " + e.getMessage(), e);
}
```

#### IllegalStateException Handling
- **Cause**: `kafkaTemplate.flush()` throws `IllegalStateException("Kafka producer is closed")`
- **Behavior**: Catch exception and continue gracefully
- **Verification**: `verify(kafkaTemplate, times(1)).flush()` - flush should still be called
- **Result**: Shutdown completes without throwing exception
- **Logging**: Should log the exception (recommended)

**Implementation**:
```java
catch (IllegalStateException e) {
    log.warn("IllegalStateException during Kafka flush: " + e.getMessage(), e);
}
```

#### General Exception Handling
The implementation should catch any exception that might occur during `kafkaTemplate.flush()`:

**Minimum Exceptions to Handle**:
- `NullPointerException`
- `IllegalStateException`

**Recommended Approach**:
```java
try {
    kafkaTemplate.flush();
} catch (NullPointerException | IllegalStateException e) {
    log.warn("Exception during Kafka flush: " + e.getMessage(), e);
} catch (Exception e) {
    log.warn("Unexpected exception during Kafka flush: " + e.getMessage(), e);
}
```

**Shutdown Completion**:
- No exception should propagate from the method
- Method always completes normally (void)
- Even if all exceptions occur, shutdown is considered successful

#### General Exception Handling
The implementation should catch any exception that might occur during `kafkaTemplate.flush()`:

**Minimum Exceptions to Handle**:
- `NullPointerException`
- `IllegalStateException`

**Recommended Approach** (already shown above in Kafka Flush Operation section)

---

## 5. Event Listener Registration

### 5.1 Spring Event Listeners
The class must be registered as a Spring component that listens to:
- `ContextRefreshedEvent` → triggers `onApplicationStartup()`
- `ContextClosedEvent` → triggers `onApplicationShutdown()`

### 5.2 Listener Pattern Options

**Option A: @EventListener Annotation**
```java
@Component
public class ApplicationLifecycleListener {
    @EventListener
    public void onApplicationStartup(ContextRefreshedEvent event) { ... }
    
    @EventListener
    public void onApplicationShutdown(ContextClosedEvent event) { ... }
}
```

**Option B: ApplicationListener Interface**
```java
@Component
public class ApplicationLifecycleListener 
        implements ApplicationListener<ContextRefreshedEvent> {
    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) { ... }
}
```

**Option C: Multiple Listeners**
```java
@Component
public class ApplicationLifecycleListener 
        implements ApplicationListener<ContextRefreshedEvent>,
                   ApplicationListener<ContextClosedEvent> {
    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        if (event instanceof ContextRefreshedEvent) { ... }
        if (event instanceof ContextClosedEvent) { ... }
    }
}
```

---

## 6. Mock Verification Summary

### 6.1 Database Operations - Startup
- `dataSource.getConnection()` - called during startup (called exactly once)
- `connection.isValid(anyInt())` - called to validate connection (parameter value not critical)

**Verification Example**:
```java
verify(dataSource, times(1)).getConnection();
verify(connection, times(1)).isValid(anyInt());
```

### 6.2 Kafka Operations - Startup
- `kafkaAdminClient.describeTopics(anyCollection())` - called exactly once per startup
- `describeTopicsResult.allTopicNames()` - called to retrieve topic names
- `kafkaFuture.get(...)` - called to wait for Kafka operation result

**Verification Example**:
```java
verify(kafkaAdminClient, times(1)).describeTopics(anyCollection());
verify(describeTopicsResult, times(1)).allTopicNames();
verify(kafkaFuture, times(1)).get(anyLong(), any(TimeUnit.class));
```

### 6.3 Kafka Operations - Shutdown
- `kafkaTemplate.flush()` - called exactly once if lifecycle enabled, zero times if disabled
- Exception handling for flush must be transparent to caller (no exceptions propagated)

**Verification Example - Enabled**:
```java
verify(kafkaTemplate, times(1)).flush();
```

**Verification Example - Disabled**:
```java
verify(kafkaTemplate, times(0)).flush();
```

---

## 7. Exception Hierarchy and Message Patterns

### 7.1 Startup Exception Pattern
All startup failures result in:
- **Exception Type**: `RuntimeException`
- **Root Cause**: Original exception (stored as cause via `new RuntimeException(message, cause)`)
- **Message Pattern**: `"Application startup failed: {component} unavailable: {reason}"`

**Component Values**:
- `"Database"` - for database connectivity failures
- `"Kafka"` - for Kafka connectivity failures

**Example Messages**:
- `"Application startup failed: Database unavailable: Connection refused"`
- `"Application startup failed: Kafka unavailable: Connection timeout"`

### 7.2 Shutdown Exception Handling
- **No exceptions should be thrown from shutdown**
- All exceptions are caught and logged
- Shutdown always completes normally

---

## 8. Shutdown Timeouts

### 8.1 Timeout Configuration
- **Kafka Flush Timeout**: 10 seconds (from `LifecycleProperties.kafkaCheckTimeout` = 10000L)
- **Total Shutdown Timeout**: 30 seconds (from `LifecycleProperties.startupTimeout` = 30000L)

### 8.2 Timeout Implementation
The Kafka flush operation must be implemented with a 10-second timeout. The entire shutdown process must complete within the 30-second total timeout.

**Example Implementation**:
```java
// 10-second Kafka flush timeout
kafkaTemplate.flush(); // With implicit timeout management

// Total shutdown must complete within 30 seconds
// (managed by Spring shutdown sequence)
```

---

## 9. Startup Timing

Startup timeouts are **NOT verified by tests**. The startup event has no timeout requirements.

---

## 10. Test Dependency Chain

### 10.1 Database-First Approach
Startup sequence must be strictly ordered:
1. Test database connectivity first
2. Only proceed to Kafka if database is available
3. This ensures database errors are caught and reported before Kafka errors

**Implementation Implication**:
```java
public void onApplicationStartup(ContextRefreshedEvent event) {
    // Database check first - if this fails, exception thrown
    checkDatabaseConnectivity();
    
    // Kafka check second - only reached if database is OK
    checkKafkaConnectivity();
}
```

### 10.2 Test Execution Order
- Database tests execute independently
- Kafka tests assume valid database connection (they mock successful database responses)
- Shutdown tests execute independently of startup tests

---

## 11. Logging Expectations

While not enforced by tests, implementation should include:

### Recommended Logging
- Use `SLF4J Logger` instance: `private static final Logger log = LoggerFactory.getLogger(ApplicationLifecycleListener.class);`
- Log messages for:
  - Database connectivity verification (info level)
  - Kafka connectivity verification (info level)
  - Startup success/failure (warn/error level)
  - Shutdown events (info level)
  - Exception occurrences (warn/error level)

### Example Log Messages
```java
log.info("Checking database connectivity...");
log.info("Database connection valid");
log.info("Checking Kafka connectivity...");
log.info("Kafka topics validated");
log.info("Application startup completed successfully");
log.error("Application startup failed: Database unavailable: {reason}");
log.info("Starting application shutdown...");
log.info("Flushing Kafka producer...");
log.warn("Exception during Kafka flush: {reason}");
log.info("Application shutdown completed");
```

---

## 12. Implementation Checklist

### Core Implementation
- Class `ApplicationLifecycleListener` created in package `com.sivalabs.ft.features.config`
- Constructor accepts `DataSource`, `KafkaTemplate`, `AdminClient`, `ApplicationProperties`
- All constructor parameters stored as instance fields
- Class registered as Spring `@Component`

### ApplicationProperties Classes
- `ApplicationProperties` class created with nested classes
- `EventsProperties` nested class with three String fields and constructor
- `LifecycleProperties` nested class with three fields (boolean, long, long) and constructor

### Startup Event Handler
- `onApplicationStartup(ContextRefreshedEvent event)` method created
- Method registered as `@EventListener` or via `ApplicationListener` interface
- Database connectivity check implemented (getConnection → isValid)
- SQLException handling with message pattern: "Application startup failed: Database unavailable: {message}"
- Kafka connectivity check implemented (describeTopics → allTopicNames → get)
- TimeoutException handling with message pattern: "Application startup failed: Kafka unavailable: {message}"
- InterruptedException handling with message pattern: "Application startup failed: Kafka unavailable: {message}"
- ExecutionException handling with message pattern: "Application startup failed: Kafka unavailable: {message}"
- Database check executed before Kafka check
- All exceptions wrapped in RuntimeException

### Shutdown Event Handler
- `onApplicationShutdown(ContextClosedEvent event)` method created
- Method registered as `@EventListener` or via `ApplicationListener` interface
- Lifecycle enabled/disabled check implemented
- No Kafka operations when lifecycle disabled
- Kafka flush called exactly once when lifecycle enabled
- NullPointerException caught and handled gracefully
- IllegalStateException caught and handled gracefully
- All exceptions caught (at minimum: NPE, ISE, general Exception)
- Method never throws exceptions (always completes normally)

### Logging
- SLF4J Logger instance created
- Log messages added for key lifecycle events
- Exception logging added

### Test Verification Requirements
- Startup calls `dataSource.getConnection()` exactly once
- Startup calls `connection.isValid(anyInt())` exactly once
- Startup calls `kafkaAdminClient.describeTopics(anyCollection())` exactly once
- Startup calls `kafkaFuture.get(...)` exactly once
- Shutdown calls `kafkaTemplate.flush()` exactly once when lifecycle enabled
- Shutdown calls `kafkaTemplate.flush()` zero times when lifecycle disabled
- Error messages match expected format patterns exactly
- All RuntimeExceptions contain original exception as cause
- Shutdown exceptions are caught and do not propagate

---

## 13. Black-Box to White-Box Transformation Summary

### What Makes This White-Box Specification

This document transforms the unit tests from black-box to white-box specifications by including:

1. **Exact method signatures** - Including parameter types, return types, and annotations
2. **Constructor parameters** - Explicit list of dependencies and their types
3. **Nested class structures** - Complete field definitions and constructor signatures
4. **Execution order** - Database check before Kafka check
5. **Exception message formats** - Exact string patterns including variable substitution
6. **Wrapping logic** - How exceptions should be caught and re-thrown
7. **Verification counts** - Exact number of times each method must be called
8. **Conditional logic** - When lifecycle is enabled vs. disabled
9. **Timeout values** - Specific timeout durations (10s Kafka, 30s total shutdown)
10. **Logging patterns** - Recommended log messages and levels
11. **Implementation patterns** - Code examples showing how to structure key operations
12. **Mock call chains** - Complete sequence of mock method calls (getConnection → isValid, describeTopics → allTopicNames → get)
13. **Spring registration** - Component registration and event listener patterns
14. **Graceful degradation** - Exception handling that allows shutdown to complete even on error

All these details enable a developer to implement `ApplicationLifecycleListener` with high confidence that their implementation will pass the unit tests.

All startup failures result in:
- **Exception Type**: `RuntimeException`
- **Root Cause**: Wrapped from original exception
- **Message Pattern**: `"Application startup failed: {component} unavailable: {reason}"`

Component values:
- `"Database"`
- `"Kafka"`

---

## 8. Shutdown Timeouts

### 8.1 Timeout Configuration
- **Kafka Flush Timeout**: 10 seconds (from `LifecycleProperties.kafkaCheckTimeout` = 10000L)
- **Total Shutdown Timeout**: 30 seconds (from `LifecycleProperties.startupTimeout` = 30000L)

The Kafka flush operation must be implemented with a 10-second timeout. The entire shutdown process must complete within the 30-second total timeout.

---

## 9. Startup Timing

Startup timeouts are **NOT verified by tests**. The startup event has no timeout requirements.

## 10. Test Dependency Chain

### Database-First Approach
Startup sequence must be:
1. Test database connectivity first
2. Only proceed to Kafka if database is available
3. This ensures database errors are caught before Kafka errors

### Test Execution Order
- Database tests execute independently
- Kafka tests assume valid database connection
- Shutdown tests execute independently of startup tests

---

## 11. Logging Expectations

While not enforced by tests, implementation should include:
- `SLF4J Logger` instance via `LoggerFactory.getLogger()`
- Log messages for:
  - Database connectivity verification
  - Kafka connectivity verification
  - Shutdown events
  - Exception occurrences