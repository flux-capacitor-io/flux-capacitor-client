## Tracking and Updating Documents

Flux Capacitor allows you to **track changes to your document store** using the `@HandleDocument` annotation.  
This enables handlers to react to document updates in real time — much like handling events.

```java

@HandleDocument
void handle(UserAccount user) {
    log.info("UserAccount {} was added or updated", user.getUserId());
}
```

### How It Works

Every time a document is (re)indexed, it receives a new message index (based on the timestamp of the update).  
Handlers annotated with `@HandleDocument` will observe these updates as they pass through the **document log**.

> ⚠️ When catching up from behind (e.g. in a replay), only the **latest version** of a document is retained per key.
> Earlier intermediate versions are not visible to late consumers.

This makes `@HandleDocument` ideal for **live processing**, **projecting the latest known state**, or
**cache rebuilds**.

---

### Transforming Stored Documents

`@HandleDocument` can also be used to **update documents in place**.

If the handler returns a **newer revision** of the document, Flux will reindex and persist the result:

```java

@HandleDocument("users")
UserAccount upgrade(UserAccount oldUser) {
    return new UserAccount(oldUser.getId(), normalizeEmail(oldUser.getEmail()));
}
```

The returned value replaces the previous document (same ID), **only if** it has a **higher `@Revision`**.

> ✅ This creates a powerful and durable upgrade path — ideal for data normalization, filling missing fields, or
> applying business logic retroactively.

To **delete** a document from the store, return `null`:

```java

@HandleDocument("users")
UserAccount upgrade(UserAccount user) {
    return user.isTestUser() ? null : user;
}
```

> Transformations are persisted reliably — even across application restarts — making this a great way to evolve your
> document model safely.

---

### Use Cases

- Auto-upcasting legacy documents
- Filling or correcting derived fields
- Cleaning up invalid or deprecated data
- Running background migrations or rehydration jobs
- Real-time analytics and change logging

---

### Handler Configuration

You can subscribe to a document collection using any of the following styles:

- `@HandleDocument(documentClass = MyModel.class)` — resolves the collection via the model’s `@Searchable` annotation
- `@HandleDocument("myCollection")` — binds directly to the named collection
- `@HandleDocument` — infers the collection from the **first parameter** of the handler method

---

### Replay for Full Collection Migration

Want to upgrade all existing documents in a collection? Combine `@HandleDocument` with replays:

1. Create a handler method that returns upgraded documents
2. Increment the `@Revision` on the model class
3. Attach a custom consumer with `@Consumer(minIndex = 0, ...)`
4. Deploy temporarily until migration completes

This is a robust and rapid way to **reindex, clean, or refactor your stored documents** in-place.

```java

@Consumer(name = "reindex-users", minIndex = 0)
class ReindexUsers {
    @HandleDocument("users")
    UserAccount upgrade(UserAccount legacyUser) {
        return fixLegacyState(legacyUser);
    }
}
```

Once the transformation is complete, the handler can be safely removed.