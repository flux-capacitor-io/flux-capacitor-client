## Payload Validation

Flux Capacitor automatically validates incoming request payloads using [JSR 380](https://beanvalidation.org/2.0/)
(Bean Validation 2.0) annotations.

This includes support for:

- `@NotNull`, `@NotBlank`, `@Size`, etc.
- `@Valid` on nested objects
- Constraint violations in command/query/webrequest payloads

If a constraint is violated, the handler method is **never called**. Instead, a `ValidationException`,
is thrown before the handler is invoked.

```java

public record CreateUser(@NotBlank String userId,
                         @NotNull @Valid UserProfile profile) {
}
```

You can disable this validation entirely by calling:

[//]: # (@formatter:off)
```java
DefaultFluxCapacitor.builder().disablePayloadValidation();
```
[//]: # (@formatter:on)

Of course, it is also easy to provide your own validation if desired. For how to do that, please refer to the section
on `HandlerInterceptors`.

> 💡 **Tip**: Flux Capacitor automatically correlates errors with the triggering message (e.g.: command or event).
>
> This means you don’t need to log extra context like the message payload or user ID — that information is already
> available in the audit trail in Flux Platform. This also encourages using **clear, user-facing error messages**
> without leaking internal details.