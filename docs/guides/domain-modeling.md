## Domain Modeling

Flux Capacitor allows you to model the state of your domain using entities that evolve over time by applying updates.
These entities — such as users, orders, etc. — maintain state and enforce invariants through controlled updates,
typically driven by commands.

### Defining the Aggregate Entity

To define a stateful domain object, annotate it with `@Aggregate`:

```java

@Aggregate
@Builder(toBuilder = true)
public record UserAccount(@EntityId UserId userId,
                          UserProfile profile,
                          boolean accountClosed) {
}
```

This `UserAccount` class models an aggregate with state such as `profile` and `accountClosed`. Each entity may contain a
field
annotated with `@EntityId` that acts as a unique identifier. For aggregates, this is optional — the aggregate itself is
typically loaded using `FluxCapacitor.loadAggregate(id)`.

An **aggregate** is a specialized root entity that serves as an entry point into a domain model. It may contain nested
child entities (modeled via `@Member`), but represents a single unit of consistency.

### 💡 **Tip:** Use strongly typed identifiers

While `@EntityId` can be placed on any object (e.g. a `String` or `UUID`), consider using a strongly typed `Id<T>` class
instead. This lets you:

- Enforce consistent ID prefixes (e.g. `"user-"`)
- Avoid collisions between entities with the same functional ID
- Enable case-insensitive matching (optional)
- Retain type information for safer entity loading and deserialization

```java
class UserId extends Id<UserAccount> {
    public UserId(String value) {
        super(value, "user-");
    }
}

@Aggregate
public record UserAccount(@EntityId UserId userId) {
}
```

Now you can easily load the entity via:

```java
Entity<UserAccount> user = FluxCapacitor.loadAggregate(new UserId("1234"));
```