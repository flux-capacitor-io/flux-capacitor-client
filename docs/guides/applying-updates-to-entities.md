### Applying Updates to entities

Entities evolve in response to **updates** — typically the payload of a command. Updates define what change should
happen and contain the logic to validate and apply those changes.

Here’s an example using two commands — one to create a user, and another to update their profile.

```java

public record CreateUser(UserId userId,
                         UserProfile profile) {

    @AssertLegal
    void assertNotExists(UserAccount current) {
        throw new IllegalCommandException("Account already exists");
    }

    @Apply
    UserAccount apply() {
        return new UserAccount(userId, profile, false);
    }
}
```

This update creates a new `UserAccount` entity after checking that no user with the same ID currently exists.

```java

public record UpdateProfile(UserId userId,
                            UserProfile profile) {

    @AssertLegal
    void assertExists(@Nullable UserAccount current) {
        if (current == null) {
            throw new IllegalCommandException("Account not found");
        }
    }

    @AssertLegal
    void assertAccountNotClosed(UserAccount current) {
        if (current.isAccountClosed()) {
            throw new IllegalCommandException("Account is closed");
        }
    }

    @Apply
    UserAccount apply(UserAccount current) {
        return current.toBuilder().profile(profile).build();
    }
}
```

> **Note**: Handler method parameters (like `UserAccount current`) are only injected if non-null. Use `@Nullable`
> to allow for missing values.

---

### Intercepting and Transforming Updates

In addition to applying and validating updates, you can also **intercept** them *before* they reach the legal or apply
phase.

Use `@InterceptApply` to:

- Suppress updates that are irrelevant or no-ops
- Rewrite updates that would otherwise be invalid
- Split a single update into multiple smaller updates

```java

@InterceptApply
Object ignoreNoChange(UserAccount current) {
    if (current.getProfile().equals(profile)) {
        return null; // suppress update, nothing to change
    }
    return this;
}
```

You can even rewrite the update entirely:

```java

@InterceptApply
UpdateProfile downgradeCommand(CreateUser command, UserAccount current) {
    //the current account could be injected, hence it already exists
    return new UpdateProfile(command.getUserId(), command.getProfile());
}
```

Or expand a bulk command into many atomic ones:

```java

@InterceptApply
List<CreateTask> expandBulk(BulkCreateTasks bulk) {
    return bulk.getTasks();
}
```

> 🔁 Flux will apply interceptors **recursively** until the final update no longer changes.

---

### Invocation Order

The update lifecycle flows as follows:

1. **Intercept** using `@InterceptApply`
2. **Assert legality** using `@AssertLegal`
3. **Apply changes** using `@Apply`

This allows you to rewrite or suppress updates *before* they’re validated or stored — a powerful tool for protecting
data integrity and simplifying update logic.

### Return Values

`@InterceptApply` supports flexible return types:

- `null` or `void` → suppress the update
- `this` → no change
- A **new update object** → rewrites the update
- A **Collection**, `Stream`, or `Optional` → emits zero or more new updates

> 📌 You typically don’t need to intercept just to avoid storing no-ops. Instead, annotate the `@Aggregate` or `@Apply`
> method with `eventPublication = IF_MODIFIED` to avoid persisting unchanged state.

---

### Summary

| Annotation        | Purpose                                        | Phase        |
|-------------------|------------------------------------------------|--------------|
| `@InterceptApply` | Rewrite, suppress, or expand updates           | Pre-check    |
| `@AssertLegal`    | Validate preconditions                         | Validation   |
| `@Apply`          | Apply state transformation                     | Execution    |

Together, these annotations offer full control over your entity update lifecycle — with a clean, declarative style.

---

### Why Keep Logic in the Updates?

While it’s possible to implement domain logic inside entities, this is **generally discouraged**. Instead, it is best
practice to define business logic directly inside **command payloads** — the updates themselves.

This update-driven approach has several advantages:

- **Behavior stays with the update** – each update class (e.g. `CreateUser`, `UpdateProfile`) encapsulates its own
  validation and transformation logic.
- **Entities stay focused** – entities remain concise, responsible only for maintaining state and enforcing invariants.
- **Easy feature cleanup** – removing an update class cleanly disables that feature.
- **Traceable domain behavior** – it’s clear what each update does and how it affects the system.

---

### Alternative: Logic in the Entity

Although possible, modeling behavior inside the aggregate can quickly become unmanageable. Here's a glimpse of what that
looks like:

```java

@Aggregate
@Builder(toBuilder = true)
public record UserAccount(@EntityId UserId userId,
                          UserProfile profile,
                          boolean accountClosed) {

    @AssertLegal
    static void assertNotExists(CreateUser update, @Nullable UserAccount user) {
        if (user != null) {
            throw new IllegalCommandException("Account already exists");
        }
    }

    @Apply
    static UserAccount create(CreateUser update) {
        return new UserAccount(update.getUserId(), update.getProfile(), false);
    }

    @AssertLegal
    static void assertExists(UpdateProfile update, @Nullable UserAccount user) {
        if (user == null) {
            throw new IllegalCommandException("Account does not exist");
        }
    }

    @AssertLegal
    void assertAccountNotClosed(UpdateProfile update) {
        if (accountClosed) {
            throw new IllegalCommandException("Account is closed");
        }
    }

    @Apply
    UserAccount update(UpdateProfile update) {
        return toBuilder().profile(update.getProfile()).build();
    }
}
```

In this model, the `UserAccount` aggregate handles all validation and transformation logic. Over time, this
centralization leads to bloat and tight coupling — especially in larger systems with many features.

---

### Mixing strategies

Flux Capacitor allows **mixed approaches**. You can define:

- `@AssertLegal` methods on the command payload
- `@Apply` methods inside the entity
- or vice versa

Just keep in mind: logic that lives in updates is **easier to test, extend, and remove**.