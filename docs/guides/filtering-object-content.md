## Filtering Object Content

Flux Capacitor provides a flexible way to **redact or tailor object content per user** using the `@FilterContent`
annotation.

This enables domain models or documents to define exactly what is visible to different users, based on roles, ownership,
or context.

### Basic Example

```java

@FilterContent
Order filter(User user) {
    return user.hasRole(Role.admin) ? this : new Order(maskSensitiveFieldsOnly());
}
```

To invoke filtering:

[//]: # (@formatter:off)
```java
Order filtered = FluxCapacitor.filterContent(order, currentUser);
```
[//]: # (@formatter:on)

### Recursive Filtering

Filtering applies **recursively** to fields and nested objects. If a nested item is a list, map, or complex structure,
it will also be filtered using its own `@FilterContent` method if present.

If a nested object returns `null` from filtering:

- It is **removed from a list**
- It is **excluded from a map**

### Root Context Injection

Filtering methods can optionally accept both:

- The current `User`
- The **root object** being filtered

This is useful for making decisions based on global context.

```java

@FilterContent
LineItem filter(User user, Order root) {
    return root.isOwner(user) ? this : null;
}
```

### Key Behaviors

- `@FilterContent` applies only when called via `FluxCapacitor.filterContent(...)` or `Serializer.filterContent(...)`
- **It is not automatic** — for performance reasons, content filtering is not applied implicitly (e.g. during search or
  document deserialization)
- If no method is annotated with `@FilterContent`, the object is returned unmodified.