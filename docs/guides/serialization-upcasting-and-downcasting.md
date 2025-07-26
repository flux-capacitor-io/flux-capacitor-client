## Serialization, Upcasting, and Downcasting

Flux Capacitor uses a `Serializer` to convert message payloads, snapshots, key-value entries, and other stored data into
a binary format (typically `byte[]`). By default, the client uses a Jackson-based implementation that serializes objects
to JSON.

The serializer is fully pluggable, and you can supply your own by implementing or extending `AbstractSerializer`.

---

### Revisions

To track changes in your data model, annotate your class with `@Revision`. When deserializing, Flux will use this
revision number to determine whether any transformation is required.

```java

@Revision(1)
public record CreateUser(String userId) { // `userId` renamed from `id`
}
```

---

### Upcasting

Upcasting transforms a serialized object from an older revision to a newer one.

```java
class CreateUserUpcaster {

    @Upcast(type = "com.example.CreateUser", revision = 0)
    ObjectNode upcastV0toV1(ObjectNode json) {
        json.set("userId", json.remove("id"));
        return json;
    }
}
```

This method is applied before deserialization. The object is transformed as needed so your code always receives the
current version.

To also modify the **type name**, return a `Data<ObjectNode>`:

```java

@Upcast(type = "com.example.CreateUser", revision = 0)
Data<ObjectNode> renameType(Data<ObjectNode> data) {
    return data.withType("com.example.RegisterUser");
}
```

You can even change a message’s `metadata` during upcasting:

```java

@Upcast(type = "com.example.CreateUser", revision = 0)
SerializedMessage changeMetadata(SerializedMessage message) {
    return message.withMetadata(
            message.getMetadata().add("timestamp",
                                      Instant.ofEpochMilli(message.getTimestamp()).toString())
    );
}
```

This can be useful for retrofitting missing fields, adding tracing info, or migrating older messages to include
required metadata keys.

---

### Dropping or Splitting Messages

Upcasters can also **drop** a message by returning `null` or `void`, or **split** it into multiple new ones:

```java

@Upcast(type = "com.example.CreateUser", revision = 0)
void dropIfDeprecated(ObjectNode json) {
    // returning void removes this message from the stream
}

@Upcast(type = "com.example.CreateUser", revision = 0)
Stream<Data<ObjectNode>> split(Data<ObjectNode> data) {
    return Stream.of(data, new Data<>(...));
}
```

This works for **any** stored data — not just messages, but also snapshots, key-value entries, and documents.

---

### Downcasting

Downcasting does the reverse: it converts a newer object into an older format. This is useful for emitting
**legacy-compatible** data or supporting **external systems**.

```java
class CreateUserDowncaster {

    @Downcast(type = "com.example.CreateUser", revision = 1)
    ObjectNode downcastV1toV0(ObjectNode json) {
        json.set("id", json.remove("userId"));
        return json;
    }
}
```

To downcast an object to a desired revision, use:

```java
FluxCapacitor.downcast(object, revision);
```

> This returns the downcasted version of the object in serialized form (e.g., as an `ObjectNode`), allowing you to
> inspect or persist the object as it would appear in an earlier revision.

---

### Registration

When using Spring, any bean containing `@Upcast` or `@Downcast` methods is **automatically registered** with the
serializer.

Outside of Spring, register them manually:

[//]: # (@formatter:off)
```java
serializer.registerCasters(new CreateUserUpcaster(), new CreateUserDowncaster());
```
[//]: # (@formatter:on)

---

### How It Works

- On **deserialization**:
    - Flux detects the revision of the stored object
    - Applies all applicable `@Upcast` methods (in order)
    - Then deserializes into the latest version

- On **serialization**:
    - Flux stores the latest type and revision
    - If needed, a `@Downcast` can adapt it for external use

All casting occurs **in your application**, not in the Flux platform. Stored messages remain unchanged.

---

### Best Practices

- Use `@Revision` to version any payloads that are stored or transmitted
- Use `ObjectNode` for simple structural changes, or `Data<ObjectNode>` to modify metadata
- Chain upcasters one revision at a time (`v0 → v1`, `v1 → v2`, etc.)
- Ensure upcasters are **side-effect free** and **deterministic**

> **Note:** Upcasting is essential when using event sourcing or durable message storage — these messages may live for
> years.