## Nested Entities

Flux Capacitor allows aggregates to contain nested entities — for example, users with authorizations or orders with line
items. These nested entities can be added, updated, or removed using the same `@Apply` pattern used for root aggregates.

To define a nested structure, annotate the collection or field with `@Member`:

```java

@Aggregate
@Builder(toBuilder = true)
public record UserAccount(@EntityId UserId userId,
                          UserProfile profile,
                          boolean accountClosed) {

    @Member
    List<Authorization> authorizations;
}
```

Child entities must define their own `@EntityId`:

```java

public record Authorization(@EntityId AuthorizationId authorizationId,
                            Grant grant) {
}
```

### Adding a Child Entity

To add a nested entity like `Authorization`, simply return a new instance from the `@Apply` method:

```java

public record AuthorizeUser(AuthorizationId authorizationId,
                            Grant grant) {

    @Apply
    Authorization apply() {
        return new Authorization(authorizationId, grant);
    }
}
```

The `UserAccount` aggregate is automatically updated to include this new child entity.

### Removing a Child Entity

To remove a nested entity, return `null` from the `@Apply` method:

```java

public record RevokeAuthorization(AuthorizationId authorizationId) {

    @AssertLegal
    void assertExists(@Nullable Authorization authorization) {
        if (authorization == null) {
            throw new IllegalCommandException("Authorization not found");
        }
    }

    @Apply
    Authorization apply(Authorization authorization) {
        return null;
    }
}
```

Flux will automatically prune the child entity with the given `authorizationId`.

> ⚠️ **Note:** When a child entity is added, updated, or removed using an `@Apply` method, Flux Capacitor will:
>
> - Automatically **locate the parent aggregate**
> - Apply the update to the child entity
> - And return a **new instance of the parent** (if it's immutable), with the updated child state included
>
> This allows you to use immutable models (e.g. Java records or classes with Lombok’s `@Value`) without extra
> boilerplate.

---

### Loading Entities and Aggregates

Flux Capacitor supports a flexible and powerful approach to loading aggregates and their internal entities using
`FluxCapacitor.loadAggregateFor(...)` and `FluxCapacitor.loadEntity(...)`.

---

#### `loadEntity(entityId)`

Use this method to load a specific entity **without needing to know the aggregate root** it belongs to. This allows APIs
to remain focused and concise—for example:

```java
public record CompleteTask(TaskId taskId) {
}
```

With Flux Capacitor, you can handle this using:

[//]: # (@formatter:off)
```java
FluxCapacitor.loadEntity(taskId).assertAndApply(new CompleteTask(taskId));
```
[//]: # (@formatter:on)

Even if the `Task` is deeply nested within a `Project` or other parent aggregate, this method works because of the
**entity relationship tracking** automatically maintained by Flux Capacitor.

Additional behavior:

- If **multiple entities** match the given ID, the one with the **most recently added relationship** is used.
- The **entire aggregate** containing the entity is loaded, ensuring consistency.
- The returned `Entity<T>` provides methods like `assertAndApply(...)` or `apply(...)`, and includes a reference to the
  enclosing aggregate root.

> This enables true *location transparency* for commands and queries: you don’t need to know or pass along the full
> aggregate path.

#### 🔁 Finding All Aggregates for an Entity

In some scenarios, an entity may be **referenced by multiple aggregates**—for example, when using shared reference
data (e.g. a `Currency`, `Role`, or `Label`). If such an entity is updated, you might want to update *all* aggregates
that reference it.

To retrieve all aggregates that currently include a given entity ID:

```java
Map<String, Class<?>> aggregates = FluxCapacitor.get()
        .aggregateRepository()
        .getAggregatesFor(myEntityId);
```

This returns a map of aggregate IDs and their types.

> ⚠️ **Caution:** This pattern should only be used if you know the number of associated aggregates will remain small and
> bounded. If many aggregates accumulate over time, this lookup can grow unbounded and lead to performance issues.

When used responsibly, this enables patterns like:

[//]: # (@formatter:off)
```java
// Rerender or update every Project referencing a shared Tag
for(Map.Entry<String, Class<?>> entry : FluxCapacitor.get()
                .aggregateRepository().getAggregatesFor(tagId).entrySet()) {
        FluxCapacitor.loadAggregate(entry.getKey(), entry.getValue())
        .apply(new RefreshTag(tagId));
}
```
[//]: # (@formatter:on)

This approach can help keep derived or denormalized data consistent across aggregates.

---

#### `loadAggregateFor(entityId)`

Use this method to retrieve the **aggregate root** that currently contains the specified entity ID.

```java
Entity<MyAggregate> aggregate = FluxCapacitor
        .loadAggregateFor("some-entity-id");
```

Behavior:

- If the ID matches a **child entity**, the enclosing aggregate is returned.
- If the ID refers to an **aggregate root**, that root is returned directly.
- If no aggregate exists, an **empty aggregate** of type `Object` is returned. This enables bootstrapping a new one by
  applying events.

> Use `loadAggregateFor(entityId, Class<T>)` when you need type safety or to avoid relying on inference.

### Alternative Entity Identifiers

Flux Capacitor supports alternative ways to reference an entity using the `@Alias` annotation. This is especially useful
when:

- The entity needs to be looked up using a secondary identifier (e.g. an email address or external ID)
- An entity wants to reference another entity without identifier collisions

#### Lookup via Aliases

Aliases are used when:

- Loading an aggregate or entity using `FluxCapacitor.loadAggregateFor(alias)` or `FluxCapacitor.loadEntity(alias)`.
- Calling `Entity#getEntity(Object id)` on a parent entity.

> If multiple entities share the same alias, behavior is undefined—avoid alias collisions unless intentional.

#### Supported Targets

You can place `@Alias` on:

- **Fields** (e.g., `@Alias String externalId`)
- **Property methods** (e.g., `@Alias String legacyId()`)

If the property is a **collection**, all non-null elements are treated as aliases. If the value is `null` or an empty
collection, it is ignored.

#### Prefix and Postfix

To avoid clashes between IDs in different domains, use the optional `prefix` and `postfix` parameters:

```java

@Alias(prefix = "email:")
String email;

@Alias(postfix = "@external")
String externalId;
```

This ensures that `email@example.com` is stored as `email:email@example.com`, and `12345` becomes `12345@external`.

#### Example

```java

public record UserAccount(@EntityId String userId,
                          @Alias(prefix = "email:") String emailAddress) {

    @Alias
    List<String> oldIds;
}
```

Now the `UserAccount` entity can be looked up using:

```java
Entity<UserAccount> entity = FluxCapacitor
        .loadEntity("email:foo@example.com");
```

or

```java
Entity<UserAccount> entity = FluxCapacitor
        .loadEntity("1234"); // one of the oldIds
```

### 💡 Tip: Use `@Alias` on Strongly-Typed `Id<T>` Identifiers

While `@Alias` can be applied to any field or property, it's often more convenient and robust to use it on a
strongly-typed identifier that extends `Id<T>`:

- `Id<T>` supports prefixing, case-insensitive matching, and type-safe deserialization.
- The `@Alias` annotation recognizes the repository ID computed by the `Id<T>` implementation.
- You don’t need to repeat the prefix in `@Alias`—it's already encoded in the `Id`.

```java
public class Email extends Id<UserAccount> {
    public Email(String email) {
        super(email, "email:");
    }
}

@Alias
Email email;
```

This allows you to load the entity by its alias:

```java
Entity<UserAccount> account = FluxCapacitor
        .loadEntity(new Email("john@example.com"));
```

This makes aliasing more explicit and reusable—particularly useful in larger applications.

### Routing Behavior

Flux automatically routes child-targeted updates like `AuthorizeUser` and `RevokeAuthorization` to the correct nested
entity using the `@EntityId`. You don’t need to write custom matching logic — the routing works transparently as long
as:

- The root aggregate is loaded (e.g. using `loadAggregate(userId)`), and
- The update contains enough identifying information to locate the nested entity

### Summary

This model leads to extremely clean domain logic:

- No need to manipulate collections in the aggregate
- No need for boilerplate logic to find, update, or remove children
- Nested updates stay localized to the child entity itself