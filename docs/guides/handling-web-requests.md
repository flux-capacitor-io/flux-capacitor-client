## Handling Web Requests

Flux Capacitor supports first-class **WebRequest handling** via the `@HandleWeb` annotation and its HTTP-specific
variants such as `@HandleGet`, `@HandlePost`, `@HandleDelete`, etc.

Instead of exposing a public HTTP server per application, Flux uses a central Web Gateway that **proxies all external
HTTP(S) and WebSocket traffic into the platform as `WebRequest` messages**. These messages are:

- **Logged** for traceability and auditing
- **Routed to client applications** using the same handler system as for commands, events, and queries
- **Handled by consumer applications** which return a `WebResponse`

#### Why This Design?

This architecture enables several key benefits:

- ✅ **Zero exposure**: client apps do not require a public-facing HTTP server and are thus *invisible* to attackers
- ✅ **Back-pressure support**: applications control load by polling their own messages
- ✅ **Audit-friendly**: every incoming request is automatically logged and correlated to its response
- ✅ **Multiple consumers possible**: multiple handlers can react to a WebRequest, though typically only one produces the
  response (others use `passive = true`)

#### Example

```java

@HandleGet("/users")
public List<UserAccount> listUsers() {
    return userService.getAllUsers();
}
```

This will match incoming GET requests to `/users` and return a list of users. The response is published back as a
`WebResponse`.

You can use the general `@HandleWeb` if you want to match multiple paths or methods or define a custom HTTP method:

```java

@HandleWeb(value = "/users/{userId}", method = {"GET", "DELETE"})
public CompletableFuture<?> handleUserRequest(WebRequest request, @PathParam String userId) {
    return switch (request.getMethod()) {
        case "GET" -> FluxCapacitor.query(new GetUser(userId));
        case "DELETE" -> FluxCapacitor.sendCommand(new DeleteUser(userId));
        default -> throw new UnsupportedOperationException();
    };
}
```

> 💡 **Tip:** To match any HTTP method without listing them explicitly, use `HttpRequestMethod.ANY`.
> This is equivalent to `"*"` and will match all incoming requests for the given path.

```java

@HandleWeb(value = "/users/{userId}", method = HttpRequestMethod.ANY)
public CompletableFuture<?> handleAllUserMethods(
        WebRequest request, @PathParam String userId) {
    // Handle any method (GET, POST, DELETE, etc.)
    ...
}
```

#### Suppressing a Response

If you want to listen to a WebRequest without generating a response (e.g. for auditing or monitoring), use
`passive = true`:

```java

@HandlePost(value = "/log", passive = true)
public void log(WebRequest request) {
    logService.store(request);
}
```

#### Dynamic Path Parameters

Use the `@PathParam` annotation to extract dynamic segments from the URI path into handler method parameters:

```java

@HandleGet("/users/{id}")
public UserAccount getUser(@PathParam String id) {
    return userService.get(id);
}
```

If the `value` is left empty, the framework will use the parameter name (`id` in this case).

#### Other Parameter Annotations

In addition to `@PathParam`, you can extract other values from the request using:

- `@QueryParam` – extract query string values
- `@HeaderParam` – extract HTTP headers
- `@CookieParam` – extract cookie values
- `@FormParam` – extract form-encoded values (for POST/PUT)

Each of these annotations supports the same rules:

- If no name is given, the method parameter name is used
- Values are automatically converted to the target parameter type

#### URI Prefixing and Composition with `@Path`

The `@Path` annotation can be used at the **package**, **class**, **method**, or **property** level to construct URI
path segments. Paths are chained together from the outermost package to the innermost handler method. If a path segment
starts with `/`, it resets the chain from that point downward.

- Empty path values use the simple name of the package.
- If placed on a field or getter, the property value is used as the path segment (enabling dynamic routing).

```java
@Path
package my.example.api;

@Path("users")
public class UserHandler {

    @Path("{id}")
    @HandleGet
    public User getUser(@PathParam String id) { ...}
}
```

This matches `/api/users/{id}`. If the class annotation had started with a `/`, i.e.: had been `@Path("/users")`, the
pattern would have become `/users/{id}`.