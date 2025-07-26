## Protecting Sensitive Data

Flux Capacitor offers built-in support for handling sensitive information with care using the `@ProtectData` and
`@DropProtectedData` annotations.

These tools help prevent sensitive fields (e.g., passwords, SSNs, tokens) from being unnecessarily stored, logged, or
retained—supporting compliance with data protection standards like **GDPR** and **ISO 27001**.

---

### `@ProtectData`: Mark Sensitive Fields

To prevent a field from being stored with the rest of a message payload, annotate it with `@ProtectData`:

```java

public record RegisterCitizen(String name,
                              @ProtectData String socialSecurityNumber) {
}
```

When this message is dispatched, the `socialSecurityNumber` will be:

- **Offloaded** to a separate data vault
- **Redacted** from the main payload (not visible in logs or message inspectors)
- **Re-injected** automatically when the message is handled

This happens transparently—you can access the field as usual in handler methods.

---

### `@DropProtectedData`: Remove When Done

If the protected data should only be retained **temporarily**, annotate the handler method with `@DropProtectedData`:

```java

@HandleCommand
@DropProtectedData
void handle(RegisterCitizen command) {
    validate(command.getSocialSecurityNumber());
    ...
}
```

Once this handler completes:

- The injected `socialSecurityNumber` is **permanently deleted** from storage.
- Future replays will deserialize the message with that field **omitted**.

> ⚠️ This mechanism ensures sensitive fields are **usable just-in-time** and **discarded thereafter**.

---

### Use Cases

- Temporary use of sensitive tokens or credentials
- Compliance with **data minimization** and **retention policies**
- Preventing accidental exposure in logs or audits

---

### Replays and Protected Data

During replays, if a message with protected fields is re-invoked **after** data has been dropped, those fields will be
missing or set to `null`. Design your handlers to tolerate this by making such fields optional or guarding usage.

---

### Notes

- Only **fields** can be annotated with `@ProtectData` (not method parameters).
- Dropping data is **irreversible**. Make sure all processing is complete before it is removed.
- Support for nested structures is currently limited — annotate each sensitive field explicitly.