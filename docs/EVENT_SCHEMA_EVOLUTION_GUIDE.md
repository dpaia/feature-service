# Event Schema Evolution and Compatibility Guide

## Overview

This document provides strategies for evolving event schemas while maintaining compatibility between producers and consumers in RabbitMQ-based event-driven architecture.

## Schema Evolution Strategies

### 1. Backward Compatibility

**Definition**: New consumers can read messages produced by old producers.

**Strategy**: Add new optional fields, never remove existing fields.

**Example**:
```java
// Version 1
public record FeatureCreatedEvent(
    Long id,
    String code,
    String title,
    String description,
    FeatureStatus status,
    String createdBy,
    Instant createdAt
) {}

// Version 2 - Backward Compatible
public record FeatureCreatedEvent(
    Long id,
    String code,
    String title,
    String description,
    FeatureStatus status,
    String createdBy,
    Instant createdAt,
    String priority  // New optional field
) {}
```

**Jackson Behavior**: Missing fields in old messages → `null` in new consumer (no error).

### 2. Forward Compatibility

**Definition**: Old consumers can read messages produced by new producers.

**Strategy**: Consumers ignore unknown fields.

**Jackson Configuration**: Enabled by default
```java
// Jackson default behavior - no configuration needed
objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
```

**Example**:
Old consumer receives new message with `priority` field → field ignored, no error.

### 3. Full Compatibility

**Definition**: Combination of backward AND forward compatibility.

**Best Practices**:
- Always add new fields as optional (nullable)
- Never remove existing fields
- Never change field types
- Use default values for missing fields
- Document all changes

## Compatibility Rules

### ✅ Safe Changes (Compatible)
1. Add new optional field: `String priority` (defaults to `null`)
2. Add new enum value: `FeatureStatus.ARCHIVED`
3. Make required field optional: Remove `@NotNull`

### ❌ Breaking Changes (Incompatible)
1. Remove existing field: `String description` deleted
2. Change field type: `Long id` → `String id`
3. Rename field: `createdBy` → `creator`
4. Change field semantics: `status` meaning changed

## Migration Strategies

### Strategy 1: Queue Versioning

Create new queues for new schema versions:

**Queues**:
- `feature-created-events-v1` (old version)
- `feature-created-events-v2` (new version)

**Process**:
1. Deploy new consumers listening to v2 queue
2. Deploy dual-write producers (write to both v1 and v2)
3. Migrate all consumers to v2
4. Remove v1 queue and dual-write logic

**Pros**: Clean separation, easy rollback  
**Cons**: More queues to manage

### Strategy 2: Message Versioning

Add version field to events:

```java
public record FeatureCreatedEvent(
    Integer schemaVersion,  // Schema version identifier
    Long id,
    String code,
    // ... other fields
) {}
```

**Consumer Logic**:
```java
if (event.schemaVersion() == 1) {
    // Handle v1 schema
} else if (event.schemaVersion() == 2) {
    // Handle v2 schema
}
```

**Pros**: Single queue, explicit versioning  
**Cons**: Consumer must handle multiple versions

### Strategy 3: Gradual Migration (Recommended)

**Phase 1 - Prepare**:
- Add new optional fields to event
- Deploy updated event definition
- Old consumers ignore new fields (forward compatible)

**Phase 2 - Populate**:
- Update producers to populate new fields
- New consumers can use new fields

**Phase 3 - Enforce** (optional):
- Make new fields required if needed
- Remove old code that doesn't use new fields

## Testing Schema Evolution

### Test Missing Fields (Backward Compatibility)

```java
@Test
void shouldDeserializeMissingFieldsAsNull() {
    String jsonWithMissingFields = """
        {
            "id": 300,
            "code": "TEST-300",
            "title": "Test"
        }
        """;
    
    // Send message
    sendMessage(jsonWithMissingFields);
    
    // Verify deserialization succeeds with null values
    FeatureCreatedEvent event = receiveEvent();
    assertThat(event.description()).isNull();
    assertThat(event.releaseCode()).isNull();
}
```

### Test Extra Fields (Forward Compatibility)

```java
@Test
void shouldIgnoreUnknownFields() {
    String jsonWithExtraFields = """
        {
            "id": 400,
            "code": "TEST-400",
            "title": "Test",
            "unknownField": "ignored",
            "anotherUnknown": 123
        }
        """;
    
    // Jackson ignores unknown fields by default
    FeatureCreatedEvent event = receiveEvent();
    assertThat(event).isNotNull(); // No deserialization error
}
```

## Best Practices

1. **Always Add, Never Remove**
   - Add new fields as optional
   - Deprecate old fields instead of removing

2. **Use Semantic Versioning**
   - Major version: Breaking changes
   - Minor version: Backward-compatible additions
   - Patch version: Bug fixes

3. **Document All Changes**
   - Maintain changelog for event schemas
   - Document field deprecations

4. **Test Compatibility**
   - Test old messages with new consumers
   - Test new messages with old consumers

5. **Default Values**
   - Provide sensible defaults for missing fields
   - Document default behavior

## Enum Evolution

### Adding New Enum Values (Safe)

```java
// Version 1
public enum FeatureStatus {
    NEW, IN_PROGRESS, RELEASED
}

// Version 2 - Add new value
public enum FeatureStatus {
    NEW, IN_PROGRESS, ON_HOLD, RELEASED  // Added ON_HOLD
}
```

**Behavior**:
- Old messages with `NEW`, `IN_PROGRESS`, `RELEASED` → work fine
- New messages with `ON_HOLD` → old consumers fail if they receive it

**Mitigation**: Use gradual rollout, deploy consumers first.

### Renaming Enum Values (Breaking)

❌ **DON'T**: Rename `IN_PROGRESS` to `IN_WORK`  
✅ **DO**: Add `IN_WORK` as alias, deprecate `IN_PROGRESS`

## Troubleshooting

### Issue: Consumer fails with unknown enum value

**Cause**: New enum value sent to old consumer  
**Solution**: 
1. Deploy updated consumers first
2. Then deploy producers using new enum values

### Issue: Field type mismatch

**Cause**: Field type changed (e.g., `Long` → `String`)  
**Solution**: This is a breaking change, requires queue versioning

### Issue: Null pointer exceptions

**Cause**: Code assumes field is not null  
**Solution**: Add null checks or use Optional<T>

## Implementation in This Project

### Current Events
- `FeatureCreatedEvent` - 9 fields
- `FeatureUpdatedEvent` - 11 fields (adds updatedBy, updatedAt)
- `FeatureDeletedEvent` - 13 fields (adds deletedBy, deletedAt)

### Nullable Fields
All fields are nullable by default (Java records without @NotNull).

### Tested Scenarios
✅ Missing fields → deserialized as `null`  
✅ Extra fields → ignored (forward compatible)  
✅ Type mismatches → rejected with error log  

### Future Evolution
To add new fields (e.g., `priority`):
1. Add field to record: `String priority`
2. Deploy consumers (handle `null` priority)
3. Deploy producers (populate priority)
4. No breaking changes!

## Conclusion

This guide provides strategies for evolving event schemas while maintaining system stability and compatibility between different versions of producers and consumers.

**Key Principle**: Make changes incrementally and test compatibility at each step.