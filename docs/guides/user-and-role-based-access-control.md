## User and Role-Based Access Control

Flux Capacitor allows you to restrict message handling based on the authenticated user's roles. This access control
happens **before** the message reaches the handler — similar to how payload validation is enforced.

There are several annotations for declaring user and role requirements:

### `@RequiresAnyRole`

Use this annotation to ensure that a handler is only invoked if the user has **at least one** of the
specified roles.

```java

@HandleCommand
@RequiresAnyRole({"admin", "editor"})
void handle(UpdateArticle command) { ...}
```

```java

@RequiresAnyRole("admin")
public record DeleteAccount(String userId) {
}
```

### `@ForbidsAnyRole`

This annotation works the other way around — it **prevents** message handling if the user has any of the specified
roles.

```java

@ForbidsAnyRole("guest")
@HandleCommand
void handle(SensitiveOperation command) { ...}
```

### `@RequiresUser`

Ensures that a message can only be handled if an **authenticated user** is present. If no user is found, the message is
rejected with an `UnauthenticatedException`.

This is useful for requiring login in scenarios like user account updates, sensitive commands, or personal data access.

```java

@RequiresUser
@HandleCommand
void handle(UpdateProfile command) { ...}
```

### `@NoUserRequired`

Allows a message to be processed even if **no authenticated user** is present — ideal for public APIs, or health checks.

```java

@NoUserRequired
@HandleCommand
void handle(SignUpUser command) { ...}
```

### `@ForbidsUser`

Prevents message handling if an **authenticated user is present**. This is useful for restricting certain flows to
unauthenticated users — such as registration endpoints, or guest-only operations.

```java
@ForbidsUser
@HandleCommand
void handle(SignUpAsGuest command) { ... }
```

---

### Controlling Behavior on Unauthorized Access

All authorization annotations include an optional `throwIfUnauthorized()` property (default: `true`) that controls what
happens when access is denied:

- If `throwIfUnauthorized = true`:
    - If a user is required but **not present**, an `UnauthenticatedException` is thrown.
    - If a user is present but **lacks the required roles**, an `UnauthorizedException` is thrown.

- If `throwIfUnauthorized = false`:
    - The handler or message is silently **skipped**, allowing delegation to other eligible handlers (if any).

This mechanism allows fine-grained control over how authentication and authorization failures are handled across your
system.

---

### Role annotations support nesting and overrides

Flux evaluates these annotations hierarchically. For example:

- If `@RequiresAnyRole("admin")` is placed on a **package**, it applies to all handlers and payloads in that package by
  default.
- You can override that requirement on a specific method or class using `@RequiresAnyRole(...)`, `@ForbidsAnyRole(...)`,
  or `@NoUserRequired`.

```java
// package-info.java
@RequiresUser
package com.myapp.handlers;
```

```java

@NoUserRequired
@HandleCommand
void handle(PublicPing ping) { ...} // Overrides the package-level requirement
```

This allows you to apply coarse-grained defaults and override them where needed.

---

### Enum-based role annotations (meta-annotations)

For more structure, you can define **custom annotations** using enums or strongly typed roles. For example:

```java
public enum Role {
    ADMIN, EDITOR, USER
}
```

```java

@RequiresAnyRole
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface RequiresRole {
    Role[] value();
}
```

You can now annotate your handlers like this:

```java

@HandleCommand
@RequiresRole(Role.ADMIN)
void handle(DeleteAccount command) { ...}
```

Flux will interpret the enum-based annotation through the underlying `@RequiresAnyRole`.

---

### Best Practices

- Use role annotations on **payload classes** to guarantee strict access checks in all environments.
- Use them on **handlers** to control fallback behavior or define role-specific processing.
- Set security defaults on **packages** or base classes, and override selectively.
- Define **custom annotations** to avoid scattering string-based role declarations.

---

> 💡 **Tip:** Access control is enforced transparently — there’s no need to log or repeat the user or message context.
> Flux automatically maintains correlation metadata between the original request and any errors, logs, or events that
> follow.

---

### Where does user info come from?

User roles are resolved by the configured `UserProvider`, which extracts the current user from message metadata (e.g.,
authentication tokens, headers, etc.). By default, Flux Capacitor uses a pluggable SPI to register this provider.

> 💡 You can override or mock this provider in tests using the TestFixture API.

---

### Providing Your Own User Logic

You can implement a custom `UserProvider` to extract users from headers, JWT tokens, cookies, etc.

```java
public class MyUserProvider extends AbstractUserProvider {
    public MyUserProvider() {
        super("Authorization", MyUser.class); // metadata key and user type
    }

    @Override
    public User fromMessage(HasMessage message) {
        if (message.toMessage() instanceof WebRequest request) {
            return decodeToken(request.getHeader("Authorization"));
        }
        return super.fromMessage(message);
    }

    private User decodeToken(String header) {
        // Implement your own token decoding and verification logic here
        return ...;
    }
}
```

This allows you to inject meaningful, application-specific `User` objects into your handlers.

---

### System and Testing Support

Your `UserProvider` implementation can also support testing and system behavior by implementing:

- `getSystemUser()` — returns a default **system-level user**, used:
    - as the default actor in tests,
    - when publishing system-side effects (e.g. from scheduled handlers).
- `getUserById(...)` — resolves a user by ID, used in test utilities like `fixture.whenCommandByUser(...)`.

This ensures your custom user logic is consistently applied, even in automated tests and background execution.

---

### 🔧 Registering your UserProvider

To enable your custom `UserProvider` (e.g., for authenticating users via headers or tokens), register it using Java's
Service Provider mechanism.

Create the file:

```
src/main/resources/META-INF/services/io.fluxcapacitor.javaclient.tracking.handling.authentication.UserProvider
```

List your implementation classes (one per line) in order of preference:

```
com.example.authentication.SenderProvider
com.example.authentication.SystemUserProvider
```

Flux Capacitor will automatically discover and register them at startup.

> 💡 If you're using Spring, your `UserProvider` can also be exposed as a bean — Flux Capacitor will pick it up
> automatically.
>
> ⚠️ However, tests **not using Spring** will not pick up the bean. For test scenarios or CLI usage, the SPI mechanism
> is still the easiest.