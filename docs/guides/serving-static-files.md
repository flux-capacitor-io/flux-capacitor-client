## Serving Static Files

Flux Capacitor supports serving static files directly from a handler class by using the `@ServeStatic` annotation.
This allows client applications to expose static resources (HTML, JS, CSS, images, etc.) without needing an external web
server.

```java

@ServeStatic(value = "/web", resourcePath = "/static")
public class WebAssets {
}
```

This will serve all files under `/static` (from the classpath or file system) under the URI path `/web`.

### Features

- Supports both **file system** and **classpath** resources
- Optional **fallback file** (e.g. for single-page apps)
- Automatic `Cache-Control` headers
- Compression via Brotli and GZIP (if precompressed variants exist)

### Annotation Reference

```java
@ServeStatic(
        value = "/assets",
        resourcePath = "/public",
        fallbackFile = "index.html",
        immutableCandidateExtensions = {"js", "css", "svg"},
        maxAgeSeconds = 86400
)
```

#### Parameters:

- `value`: Web path(s) where static content is served. Relative paths are prefixed by `@Path` values.
- `resourcePath`: The resource root (either on the file system or classpath).
- `fallbackFile`: A file to serve when the requested path doesn’t exist (e.g. `index.html`). Set to `""` to disable.
- `immutableCandidateExtensions`: Extensions that are eligible for aggressive caching if fingerprinted (e.g.
  `main.123abc.js`).
- `maxAgeSeconds`: Default cache duration for non-immutable resources.

### Example: Serving a React App

```java

@ServeStatic(value = "/app", resourcePath = "/static", fallbackFile = "index.html")
public class WebFrontend { ...
}
```

This will serve files under `/app/**` and fallback to `index.html` for unknown paths—ideal for single-page apps.

> 📁 Files in `/static` on the classpath (e.g. under `resources/static/`) or `/static` on disk will be served.

> When both file system and classpath contain a file, the **file system takes precedence**.

> If the `resourcePath` starts with `classpath:`, **only classpath resources** are served.  
> If it starts with `file:`, **only file system resources** are served.

This allows precise control over where content is loaded from and ensures classpath-only or file-system-only resolution
depending on the use case.

#### Combining Static and Dynamic Handlers

You can freely combine `@ServeStatic` with dynamic handler methods in the same class or package.

This is especially useful when your application serves a combination of:

- **Static assets** like HTML, JS, CSS
- **Dynamic endpoints** like REST APIs or view-rendered pages

```java

@Path("/app")
@ServeStatic("static") // serves static files from the /static resource directory for web paths /app/static/**
public class AppController {

    @HandleGet("/status")
    Status getStatus() {
        return new Status("OK", Instant.now());
    }

    @HandlePost("/submit")
    SubmissionResult submitForm(FormData data) {
        return formService.handle(data);
    }
}
```

This will:

- Serve `/app/static/index.html`, `/app/static/styles.css`, etc. from the `static/` resource directory
- Also respond to `/app/status` and `/app/submit` dynamically

The static file handling applies to all routes **not matched** by other methods in the class. This makes it ideal for
combining SPAs or hybrid web apps with API endpoints under a shared route prefix.