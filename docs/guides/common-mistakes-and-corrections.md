# Common FluxZero Mistakes and Corrections

This document captures common mistakes when implementing FluxZero applications and their corrections.

## Message Handler Annotations

### ❌ Wrong Import
```kotlin
import io.fluxcapacitor.javaclient.modeling.HandleCommand  // WRONG!
import io.fluxcapacitor.javaclient.modeling.HandleQuery    // WRONG!
```

### ✅ Correct Import
```kotlin
import io.fluxcapacitor.javaclient.tracking.handling.HandleCommand  // CORRECT
import io.fluxcapacitor.javaclient.tracking.handling.HandleQuery    // CORRECT
```

## Exception Handling

### ❌ Wrong Exception Classes
```kotlin
import io.fluxcapacitor.javaclient.persisting.search.IllegalCommandException  // WRONG!
throw IllegalCommandException("Some error")  // WRONG!
```

### ✅ Correct Exception Classes
Use standard Java exceptions for business rule violations:
```kotlin
throw IllegalArgumentException("Invalid argument")     // For invalid inputs
throw IllegalStateException("Invalid state")           // For business rule violations
throw UnsupportedOperationException("Not supported")   // For unsupported operations
```

## Entity Deletion

### ❌ Wrong Deletion Pattern
```kotlin
@HandleCommand
fun handle(command: DeleteEntity, entity: Entity<SomeType>) {
    entity.delete()  // WRONG! No such method exists
}
```

### ✅ Correct Deletion Pattern
```kotlin
@HandleCommand
fun handle(command: DeleteEntity, entity: Entity<SomeType>): SomeType? {
    // Returning null effectively deletes the aggregate
    return null
}
```

## Method Name Collisions

### ❌ Property and Method Name Collision
```kotlin
data class MyEntity(
    val versions: List<Version>  // Property named 'versions'
) {
    fun getVersions(): List<String> = ...  // WRONG! Collides with auto-generated getter
}
```

### ✅ Avoid Collisions with Different Names
```kotlin
data class MyEntity(
    val versions: List<Version>  // Property named 'versions'
) {
    fun getVersionUrls(): List<String> = versions.map { it.toUrl() }  // CORRECT
}
```

## Search and Querying

### ❌ Wrong Search API Usage
```kotlin
@HandleQuery
fun handle(query: ListItems): List<Item> {
    return FluxCapacitor.search(Item::class.java)  // WRONG! Requires complex setup
        .fetchAll()
        .map { it.get() }
}
```

### ✅ Simple List Implementation
```kotlin
@HandleQuery
fun handle(query: ListItems): List<Item> {
    // For simple cases, return empty list or use other means
    // Proper search requires indexing configuration
    return emptyList()
}
```

## Handler Organization Patterns

### ✅ Commands: Handlers Inside Command Classes with Domain-Specific Interfaces
Commands should have handlers inside the command class using domain-specific interfaces with @Apply pattern. Use specialized interfaces for different types of operations:

#### ObjectType Commands Interface
```kotlin
// For commands that operate on the ObjectType aggregate
@TrackSelf
interface ObjectTypeCommand {
    val objectTypeId: ObjectTypeId
    
    @HandleCommand
    fun handle(): ObjectType {
        return FluxCapacitor.loadAggregate(objectTypeId).assertAndApply(this).get()
    }
}
```

#### ObjectType Version Commands Interface
```kotlin
// For commands that operate on specific ObjectType versions
@TrackSelf
interface ObjectTypeVersionCommand {
    val versionId: ObjectTypeVersionId
    // FluxCapacitor automatically finds the ObjectType containing this version
    // and injects it into @Apply and @AssertLegal methods
}
```

#### Examples using ObjectTypeCommand (Aggregate-level operations)

```kotlin
// Command for creating new aggregates
data class CreateObjectType(
    override val objectTypeId: ObjectTypeId,
    val name: String,
    val namePlural: String,
    val initialJsonSchema: JsonNode
    // ... other properties
) : Request<ObjectType>, ObjectTypeCommand {

    @Apply
    fun apply(): ObjectType {
        // Create new ObjectType with initial version
        return ObjectType(...)
    }
}

// Command that operates on existing aggregates
data class UpdateObjectType(
    override val objectTypeId: ObjectTypeId,
    val name: String? = null,
    val namePlural: String? = null
    // ... other properties
) : Request<ObjectType>, ObjectTypeCommand {

    @Apply
    fun apply(objectType: ObjectType): ObjectType {
        return objectType.copy(
            name = name ?: objectType.name,
            namePlural = namePlural ?: objectType.namePlural
            // ... other updates
        )
    }
}
```

#### Examples using ObjectTypeVersionCommand (Version-specific operations)

```kotlin
// Command that operates on specific versions
data class UpdateObjectTypeVersion(
    val id: ObjectTypeVersionId,
    val jsonSchema: JsonNode? = null,
    val status: VersionStatus? = null
) : Request<ObjectType>, ObjectTypeVersionCommand {

    override val versionId = id

    @AssertLegal
    fun validateBusinessRules(objectType: ObjectType) {
        val existingVersion = objectType.versions.find { it.id == id }
            ?: throw IllegalArgumentException("Version not found: $id")
        
        if (existingVersion.status == VersionStatus.PUBLISHED) {
            throw IllegalStateException("Cannot modify published version: $id")
        }
    }

    @Apply
    fun apply(objectType: ObjectType): ObjectType {
        // ObjectType parameter is automatically injected by FluxCapacitor
        // Update specific version within the aggregate
        val updatedVersions = objectType.versions.map { 
            if (it.id == id) it.copy(jsonSchema = jsonSchema ?: it.jsonSchema) else it 
        }
        return objectType.copy(versions = updatedVersions)
    }
}

// Command that deletes specific versions
data class DeleteObjectTypeVersion(
    val id: ObjectTypeVersionId
) : Request<ObjectType>, ObjectTypeVersionCommand {

    override val versionId = id

    @Apply
    fun apply(objectType: ObjectType): ObjectType {
        // ObjectType parameter is automatically injected by FluxCapacitor
        val updatedVersions = objectType.versions.filter { it.id != id }
        return objectType.copy(versions = updatedVersions)
    }
}
```

### ✅ Queries: Handlers Inside Query Classes
Queries should have handlers as methods inside the query class itself:

```kotlin
// Query with handler inside the class
data class GetUser(
    val id: UserId
) : Request<User?> {
    
    @HandleQuery
    fun handle(): User? {
        return FluxCapacitor.loadAggregate(id).get()
    }
}

data class ListUsers(
    val page: Int = 1,
    val pageSize: Int = 20
) : Request<List<User>> {
    
    @HandleQuery
    fun handle(): List<User> {
        // Query logic here
        return emptyList()
    }
}
```

### ❌ Wrong: All Handlers in One Large Class
```kotlin
@Component
class MegaHandler {
    @HandleCommand fun handle(cmd1: Command1): Result1 { ... }
    @HandleCommand fun handle(cmd2: Command2): Result2 { ... }
    @HandleQuery fun handle(query1: Query1): Result1 { ... }
    @HandleQuery fun handle(query2: Query2): Result2 { ... }
    // ... 20 more handlers - TOO BIG!
}
```

### ❌ Wrong: Query Handlers in Separate Classes
```kotlin
// DON'T DO THIS for queries
@Component
class UserQueryHandler {
    @HandleQuery
    fun handle(query: GetUser): User? { ... }
}
```

### 📝 Why This Pattern?

**Commands** benefit from being inside the command class because:
- Handler logic has direct access to command properties
- Domain-specific interfaces handle common boilerplate (aggregate loading)
- No need for separate handler classes that just delegate
- Command and its logic stay together for better cohesion
- Proper FluxCapacitor @Apply pattern for event sourcing

**Queries** are usually simple data retrieval operations that:
- Have direct access to query parameters
- Don't require external dependencies
- Keep query logic close to the query definition
- Are more readable and maintainable

### 🔧 Domain-Specific Interface Benefits

#### ObjectTypeCommand Interface
The `ObjectTypeCommand` interface provides:
- **@TrackSelf** for asynchronous processing and message tracking
- **@HandleCommand** built into the interface - no need to repeat
- **Automatic aggregate loading** using `assertAndApply(this)`
- **@Apply pattern integration** - FluxCapacitor calls @Apply methods automatically
- **Zero boilerplate** - no manual `FluxCapacitor.loadAggregate()` calls
- **Type safety** specific to ObjectType domain
- **Consistent pattern** across all ObjectType commands

#### ObjectTypeVersionCommand Interface
The `ObjectTypeVersionCommand` interface provides all ObjectTypeCommand benefits plus:
- **Version-specific context** with only `versionId` needed
- **Automatic ObjectType injection** - FluxCapacitor finds the containing ObjectType automatically
- **Specialized interface** for operations that target specific versions
- **Clear separation** between aggregate-level and version-level operations
- **Enhanced readability** - makes it obvious when a command operates on versions
- **Zero boilerplate** - no need to specify ObjectTypeId, it's derived automatically
- **Future extensibility** - can add version-specific utilities and validations

#### When to Use Which Interface

**Use ObjectTypeCommand for:**
- Creating new ObjectTypes
- Updating ObjectType metadata (name, description, etc.)
- Deleting entire ObjectTypes
- Creating new versions (aggregate-level operation)

**Use ObjectTypeVersionCommand for:**
- Updating specific version data (schema, status)
- Deleting specific versions
- Version-specific validation and business rules
- Operations that need access to both the version and its containing ObjectType

## Command Validation Patterns

### ✅ Use JSR303 for Field Validation
For basic field validation, use standard JSR303 annotations:

```kotlin
data class UpdateObjectTypeVersion(
    @field:NotNull(message = "ObjectType version ID is required")
    val id: ObjectTypeVersionId,
    
    @field:Size(min = 1, message = "JSON schema cannot be empty")
    val jsonSchema: JsonNode? = null,
    
    val status: VersionStatus? = null
) : Request<ObjectType>, ObjectTypeCommand
```

### ✅ Use @AssertLegal for Business Rule Validation
For business rules that require the aggregate state, use @AssertLegal:

```kotlin
data class UpdateObjectTypeVersion(
    @field:NotNull(message = "ObjectType version ID is required")
    val id: ObjectTypeVersionId,
    val jsonSchema: JsonNode? = null,
    val status: VersionStatus? = null
) : Request<ObjectType>, ObjectTypeCommand {

    override val objectTypeId = id.objectTypeId

    @AssertLegal
    fun validateBusinessRules(objectType: ObjectType) {
        val existingVersion = objectType.versions.find { it.id == id }
            ?: throw IllegalArgumentException("ObjectType version not found: $id")
        
        if (existingVersion.status == VersionStatus.PUBLISHED) {
            throw IllegalStateException("Cannot modify published version: $id")
        }
        
        // Additional business rules
        if (existingVersion.status == VersionStatus.DEPRECATED && 
            status != null && status != VersionStatus.DEPRECATED) {
            throw IllegalStateException("Cannot change status from DEPRECATED to $status")
        }
    }

    @Apply
    fun apply(objectType: ObjectType): ObjectType {
        // Business logic here - validation already passed
    }
}
```

### 📋 Validation Order
FluxCapacitor processes validation in this order:
1. **JSR303 validation** on command fields
2. **@AssertLegal methods** with aggregate state
3. **@Apply method** execution

## Request Interface Implementation

### ✅ Best Practice: Implement Request Interface
All commands and queries should implement the `Request<T>` interface to define their return type. This provides:

- **Type Safety**: Compile-time verification of return types
- **Better IDE Support**: Auto-completion and refactoring
- **Self-Documenting**: Clear contract of what each request returns
- **Consistency**: Standardized approach across the application

```kotlin
import io.fluxcapacitor.javaclient.tracking.handling.Request

// Command with return type
data class CreateUser(
    val name: String,
    val email: String
) : Request<User>

// Query with return type  
data class GetUser(
    val id: UserId
) : Request<User?>

// Command that may return null (for deletion)
data class DeleteUser(
    val id: UserId  
) : Request<User?>

// Query that returns a list
data class ListUsers(
    val page: Int = 1,
    val pageSize: Int = 20
) : Request<List<User>>
```

### ✅ Handler Method Signatures
When implementing Request interface, handler methods have cleaner signatures:

```kotlin
@Component
class UserCommandHandler {
    
    @HandleCommand
    fun handle(command: CreateUser): User {
        // Return type is explicitly defined by Request<User>
        return User(...)
    }
    
    @HandleCommand  
    fun handle(command: DeleteUser): User? {
        // Return type is explicitly defined by Request<User?>
        return null  // Deletes the entity
    }
}

@Component
class UserQueryHandler {
    
    @HandleQuery
    fun handle(query: GetUser): User? {
        // Return type is explicitly defined by Request<User?>
        return FluxCapacitor.loadAggregate(query.id).get()
    }
    
    @HandleQuery
    fun handle(query: ListUsers): List<User> {
        // Return type is explicitly defined by Request<List<User>>
        return emptyList()
    }
}
```

## Time Handling

### ❌ Wrong: Using System Time
```kotlin
val now = LocalDate.now()  // WRONG! Not testable
val timestamp = Instant.now()  // WRONG! Not testable
```

### ✅ Correct: Using FluxCapacitor Clock
```kotlin
// For LocalDate
val now = LocalDate.from(FluxCapacitor.currentClock().instant().atZone(FluxCapacitor.currentClock().zone))

// For Instant
val timestamp = FluxCapacitor.currentClock().instant()

// For ZonedDateTime
val zonedNow = FluxCapacitor.currentClock().instant().atZone(FluxCapacitor.currentClock().zone)
```

### 📋 Why Use FluxCapacitor.currentClock()?

FluxCapacitor provides a controllable clock that:
- **Enables testing** with fixed or controlled time
- **Supports time travel** in tests
- **Maintains consistency** across distributed systems
- **Allows time mocking** without external libraries

## Testing Patterns

### ✅ Use TestFixture for Command and Query Testing

FluxZero provides TestFixture for comprehensive testing of commands and queries:

```kotlin
class ObjectTypeCommandsTest {
    private val testFixture = TestFixture.create()

    @Test
    fun `should create ObjectType with proper validation`() {
        val command = CreateObjectType(...)
        
        testFixture
            .whenCommand(command)
            .expectResult { result: ObjectType ->
                result.name == "Person" &&
                result.versions.size == 1 &&
                result.versions[0].status == VersionStatus.DRAFT
            }
    }
}
```

### ✅ Test Command Workflows with andThen()

Chain multiple commands to test complete workflows:

```kotlin
@Test
fun `should complete full lifecycle workflow`() {
    testFixture
        .whenCommand(createCommand)
        .expectResult { /* assertions */ }
        .andThen()
        .whenCommand(updateCommand)
        .expectResult { /* assertions */ }
        .andThen()
        .whenCommand(deleteCommand)
        .expectResult { result: ObjectType? -> result == null }
}
```

### ✅ Test Validation Failures

Test both JSR303 and @AssertLegal validation:

```kotlin
@Test
fun `should fail to update published version`() {
    testFixture
        .givenCommands(createCommand, publishCommand)
        .whenCommand(updatePublishedCommand)
        .expectExceptionalResult(IllegalStateException::class.java)
}
```

### ✅ Use External JSON Files for Test Data

Keep test data in JSON files for reusability:

```kotlin
@Test
fun `should process command from JSON file`() {
    testFixture
        .whenCommand("objecttypes/create-person-objecttype.json")
        .expectResult { /* assertions */ }
}
```

JSON structure with @class annotation:
```json
{
  "@class": "com.example.flux.objecttypes.commands.CreateObjectType",
  "objectTypeId": {
    "@class": "com.example.flux.objecttypes.ObjectTypeId",
    "uuid": "550e8400-e29b-41d4-a716-446655440000"
  },
  "name": "Person"
}
```

### ✅ Test Time-Dependent Logic

Use FluxCapacitor's time control for testing time-dependent behavior:

```kotlin
@Test
fun `should handle time-dependent operations`() {
    testFixture
        .whenCommand(command)
        .expectResult { result -> result.createdAt != null }
        .andThen()
        .whenTimeElapses(Duration.ofDays(1))
        .whenCommand(laterCommand)
        .expectResult { /* time-based assertions */ }
}
```

### 📋 Testing Best Practices

1. **Test all command scenarios**: Happy path, validation failures, business rule violations
2. **Use meaningful assertions**: Verify specific properties, not just non-null
3. **Test workflows**: Use `andThen()` to test command sequences
4. **Separate test concerns**: One test per scenario, clear test names
5. **Use JSON files**: For complex test data and reusable scenarios
6. **Test exceptions**: Verify proper error handling with `expectExceptionalResult()`
7. **Verify state changes**: Check aggregate state after commands
8. **Test time handling**: Verify `FluxCapacitor.currentClock()` usage

## Import Checklist

When implementing FluxZero handlers, always use these imports:

```kotlin
// For handler annotations
import io.fluxcapacitor.javaclient.tracking.handling.HandleCommand
import io.fluxcapacitor.javaclient.tracking.handling.HandleQuery

// For Request interface (BEST PRACTICE)
import io.fluxcapacitor.javaclient.tracking.handling.Request

// For domain modeling
import io.fluxcapacitor.javaclient.modeling.Aggregate
import io.fluxcapacitor.javaclient.modeling.EntityId
import io.fluxcapacitor.javaclient.modeling.Member
import io.fluxcapacitor.javaclient.modeling.Entity

// For FluxCapacitor client operations
import io.fluxcapacitor.javaclient.FluxCapacitor

// Standard exceptions (no special FluxCapacitor exceptions needed)
// Use: IllegalArgumentException, IllegalStateException, UnsupportedOperationException
```